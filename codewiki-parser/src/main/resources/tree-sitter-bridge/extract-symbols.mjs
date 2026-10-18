#!/usr/bin/env node
import { readFileSync } from "fs";
import Parser from "tree-sitter";

const LANGUAGE_LOADERS = {
  java: () => import("tree-sitter-java").then(m => m.default),
  python: () => import("tree-sitter-python").then(m => m.default),
  typescript: () => import("tree-sitter-typescript").then(m => m.default.typescript),
};

const QUERIES = {
  java: `
    (class_declaration name: (identifier) @name) @class_def
    (interface_declaration name: (identifier) @name) @interface_def
    (enum_declaration name: (identifier) @name) @enum_def
    (method_declaration name: (identifier) @name) @method_def
    (constructor_declaration name: (identifier) @name) @constructor_def
  `,
  python: `
    (class_definition name: (identifier) @name) @class_def
    (function_definition name: (identifier) @name) @function_def
  `,
  // NOTE: TypeScript uses type_identifier for class/interface names
  typescript: `
    (class_declaration name: (type_identifier) @name) @class_def
    (interface_declaration name: (type_identifier) @name) @interface_def
    (enum_declaration name: (identifier) @name) @enum_def
    (method_definition name: (property_identifier) @name) @method_def
    (function_declaration name: (identifier) @name) @function_def
    (arrow_function) @arrow_def
  `,
};

const TYPE_MAP = {
  class_def: "class",
  interface_def: "interface",
  enum_def: "enum",
  method_def: "method",
  constructor_def: "constructor",
  function_def: "function",
  arrow_def: "arrow_function",
};

function ownerTypeOf(node, source) {
  let parent = node.parent;
  while (parent) {
    if (["class_declaration", "interface_declaration", "enum_declaration"].includes(parent.type)) {
      const name = parent.childForFieldName?.("name");
      return name ? source.substring(name.startIndex, name.endIndex) : null;
    }
    parent = parent.parent;
  }
  return null;
}

// Restrict the first implementation to simple nominal Java types; generic/array/
// inferred/qualified chains and mutations are left unresolved rather than guessed.
function nominalType(typeNode, source) {
  if (!typeNode) return null;
  const raw = source.substring(typeNode.startIndex, typeNode.endIndex).trim();
  return /^[A-Za-z_$][\w$]*$/.test(raw) && !["var", "void"].includes(raw) ? raw : null;
}

function isAncestorScope(scope, node) {
  let current = node;
  while (current) {
    if (current === scope) return true;
    current = current.parent;
  }
  return false;
}

function javaReceiverType(methodNode, invocation, receiver, source) {
  if (!receiver || !/^(?:this\.)?[a-zA-Z_$][\w$]*$/.test(receiver)) return null;
  const fieldOnly = receiver.startsWith("this.");
  const name = fieldOnly ? receiver.slice(5) : receiver;
  const methodBody = methodNode.childForFieldName?.("body");
  const definitions = [];
  const consider = (declaration, scope, rank) => {
    if (!scope || !isAncestorScope(scope, invocation)) return;
    if (rank !== 0 && declaration.startIndex >= invocation.startIndex) return;
    const type = nominalType(declaration.childForFieldName?.("type"), source);
    if (!type) return;
    const declarators = declaration.type === "formal_parameter"
      ? [declaration] : declaration.namedChildren.filter(n => n.type === "variable_declarator");
    for (const varNode of declarators) {
      const id = varNode.childForFieldName?.("name");
      if (id && source.substring(id.startIndex, id.endIndex) === name) {
        definitions.push({type, rank, position: declaration.startIndex});
      }
    }
  };
  if (!fieldOnly) {
    const params = methodNode.childForFieldName?.("parameters");
    for (const param of params?.namedChildren || []) {
      if (param.type === "formal_parameter") consider(param, methodNode, 100);
    }
    const walk = n => {
      if (n !== methodBody && ["class_declaration", "method_declaration", "lambda_expression"].includes(n.type)) return;
      if (n.type === "local_variable_declaration") consider(n, n.parent, 200);
      for (const child of n.namedChildren) walk(child);
    };
    if (methodBody) walk(methodBody);
  }
  let clazz = methodNode.parent;
  while (clazz && clazz.type !== "class_declaration") clazz = clazz.parent;
  if (clazz) {
    const body = clazz.childForFieldName?.("body");
    for (const field of body?.namedChildren || []) {
      if (field.type === "field_declaration") consider(field, clazz, 0);
    }
  }
  if (!definitions.length) return null;
  const maxRank = Math.max(...definitions.map(d => d.rank));
  const scopeCandidates = definitions.filter(d => d.rank === maxRank);
  // Multiple visible declarations at the same priority mean ambiguous scope.
  if (scopeCandidates.length !== 1) return null;
  const type = scopeCandidates[0].type;
  // Without full flow analysis an assignment/update can change a receiver's type.
  // Conservatively drop a receiver when it is reassigned before the invocation.
  if (!fieldOnly && methodBody) {
    let mutation = false;
    const walk = n => {
      if (n.startIndex >= invocation.startIndex || mutation) return;
      if (["assignment_expression", "update_expression"].includes(n.type)) {
        const lhs = n.childForFieldName?.("left") || n.namedChildren[0];
        if (lhs && source.substring(lhs.startIndex, lhs.endIndex) === name) mutation = true;
      }
      for (const child of n.namedChildren) walk(child);
    };
    walk(methodBody);
    if (mutation) return null;
  }
  return type;
}

function javaCalls(node, source) {
  const calls = [];
  const visit = current => {
    if (current.type === "method_invocation") {
      const name = current.childForFieldName("name");
      const receiver = current.childForFieldName("object");
      if (name) {
        const value = source.substring(name.startIndex, name.endIndex);
        const objectText = receiver && source.substring(receiver.startIndex, receiver.endIndex);
        const normalizedReceiver = objectText && objectText.length < 128 ? objectText : null;
        calls.push({kind: "call", name: value,
          receiver: normalizedReceiver,
          receiverType: javaReceiverType(node, current, normalizedReceiver, source),
          line: current.startPosition.row + 1});
      }
    }
    // Calls in a nested class/method belong to that nested symbol, not its parent.
    for (const child of current.namedChildren) {
      if (child !== node && ["method_declaration", "constructor_declaration",
          "class_declaration", "interface_declaration"].includes(child.type)) continue;
      visit(child);
    }
  };
  visit(node);
  return calls;
}

function extractSymbols(tree, source, queryStr, langObj, filePath, lang) {
  const imports = lang === "java" ? tree.rootNode.namedChildren
    .filter(n => n.type === "import_declaration")
    .map(n => source.substring(n.startIndex, n.endIndex).trim()) : [];
  const query = new Parser.Query(langObj, queryStr);
  const matches = query.matches(tree.rootNode);
  const symbols = [];

  for (const match of matches) {
    const typeCapture = match.captures.find(c => c.name !== "name");
    const nameCapture = match.captures.find(c => c.name === "name");

    if (!typeCapture) continue;

    const node = typeCapture.node;
    const type = TYPE_MAP[typeCapture.name] || typeCapture.name;

    let name = "<anonymous>";
    if (nameCapture) {
      name = source.substring(nameCapture.node.startIndex, nameCapture.node.endIndex);
    }

    const bodyChild = node.childForFieldName?.("body");
    const sigEnd = bodyChild ? bodyChild.startIndex : node.endIndex;
    const signature = source
      .substring(node.startIndex, sigEnd)
      .replace(/\n\s*/g, " ")
      .trim();

    symbols.push({
      name,
      type,
      signature,
      startLine: node.startPosition.row + 1,
      endLine: node.endPosition.row + 1,
      sourceCode: source.substring(node.startIndex, node.endIndex),
      filePath,
      ownerType: lang === "java" ? ownerTypeOf(node, source) : null,
      imports: lang === "java" ? imports : null,
      references: lang === "java" ? (type === "method" || type === "constructor"
        ? javaCalls(node, source) : []) : null,
    });
  }

  return symbols;
}

async function batchMain() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  const files = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  if (!Array.isArray(files) || files.length > 800 ||
      files.some(f => typeof f !== "string")) {
    throw new Error("invalid batch file list");
  }
  const parsers = new Map();
  const symbols = [];
  for (const filePath of files) {
    const ext = filePath.toLowerCase().match(/\.(java|py|ts|tsx)$/)?.[1];
    const lang = ({java: "java", py: "python", ts: "typescript", tsx: "typescript"})[ext];
    if (!lang) throw new Error("unsupported source language");
    if (!parsers.has(lang)) {
      const langObj = await LANGUAGE_LOADERS[lang]();
      const parser = new Parser();
      parser.setLanguage(langObj);
      parsers.set(lang, {parser, langObj});
    }
    const {parser, langObj} = parsers.get(lang);
    const source = readFileSync(filePath, "utf8");
    symbols.push(...extractSymbols(parser.parse(source), source, QUERIES[lang], langObj, filePath, lang));
  }
  console.log(JSON.stringify(symbols));
}

async function main() {
  const args = process.argv.slice(2);
  if (args.includes("--batch")) {
    await batchMain();
    return;
  }
  const langIdx = args.indexOf("--lang");
  const fileIdx = args.indexOf("--file");

  if (langIdx === -1 || fileIdx === -1) {
    console.error("Usage: extract-symbols.mjs --lang <java|python|typescript> --file <path>");
    process.exit(1);
  }

  const lang = args[langIdx + 1];
  const filePath = args[fileIdx + 1];

  if (!LANGUAGE_LOADERS[lang]) {
    console.error("Unsupported language:", lang);
    process.exit(1);
  }

  const langObj = await LANGUAGE_LOADERS[lang]();
  const parser = new Parser();
  parser.setLanguage(langObj);

  const source = readFileSync(filePath, "utf-8");
  const tree = parser.parse(source);
  const symbols = extractSymbols(tree, source, QUERIES[lang], langObj, filePath, lang);

  console.log(JSON.stringify(symbols, null, 2));
}

main().catch(e => {
  console.error(e.message);
  process.exit(1);
});
