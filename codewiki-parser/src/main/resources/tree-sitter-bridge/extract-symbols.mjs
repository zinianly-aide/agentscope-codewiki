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

function extractSymbols(tree, source, queryStr, langObj, filePath) {
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
    });
  }

  return symbols;
}

async function main() {
  const args = process.argv.slice(2);
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
  const symbols = extractSymbols(tree, source, QUERIES[lang], langObj, filePath);

  console.log(JSON.stringify(symbols, null, 2));
}

main().catch(e => {
  console.error(e.message);
  process.exit(1);
});
