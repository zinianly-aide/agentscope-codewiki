#!/usr/bin/env python3
"""Compare a CodeWiki graph JSON with independently annotated CALLS edges.

Gold JSON format: [{"callerPath": ..., "callerDeclarationLine": 42,
                    "targetPath": ..., "targetDeclarationLine": 8}, ...]
This is a local, deterministic metric calculation; it does NOT create gold labels.
"""
import argparse
import json
from pathlib import Path

REQUIRED = ('callerPath', 'callerDeclarationLine', 'targetPath', 'targetDeclarationLine')


def label(item):
    if not isinstance(item, dict) or any(name not in item for name in REQUIRED):
        raise ValueError('Each gold label requires callerPath, callerDeclarationLine, '
                         'targetPath, targetDeclarationLine')
    value = tuple(item[name] for name in REQUIRED)
    if any(not isinstance(v, str) or not v for v in (value[0], value[2])):
        raise ValueError('Gold source paths must be non-empty strings')
    if any(type(v) is not int or v <= 0 for v in (value[1], value[3])):
        raise ValueError('Gold declaration lines must be positive integers')
    return value


def compare(graph, gold):
    if not isinstance(graph.get('nodes'), list) or not isinstance(graph.get('edges'), list):
        raise ValueError('Graph JSON must contain nodes and edges arrays')
    if not isinstance(gold, list):
        raise ValueError('Gold JSON must be an array of call-edge labels')
    nodes = {n['id']: n for n in graph['nodes']}
    predicted = set()
    for edge in graph['edges']:
        if edge.get('type') != 'CALLS':
            continue
        src, dst = nodes.get(edge.get('sourceId')), nodes.get(edge.get('targetId'))
        if src is None or dst is None:
            raise ValueError('Call edge references a missing graph node')
        predicted.add((src['filePath'], src['startLine'], dst['filePath'], dst['startLine']))
    labeled = {label(row) for row in gold}
    tp = predicted & labeled
    fp = predicted - labeled
    fn = labeled - predicted
    precision = len(tp) / len(predicted) if predicted else (1.0 if not labeled else 0.0)
    recall = len(tp) / len(labeled) if labeled else 1.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        'truePositives': len(tp), 'falsePositives': len(fp), 'falseNegatives': len(fn),
        'precision': round(precision, 6), 'recall': round(recall, 6),
        'f1': round(f1, 6), 'predictedCalls': len(predicted),
        'labeledCalls': len(labeled),
        'falsePositiveExamples': sorted(fp), 'falseNegativeExamples': sorted(fn),
        'note': 'Scores are meaningful only when the independent gold set is complete '
                'for all evaluated calls. Never treat unlabeled true calls as errors.'
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--graph', required=True, type=Path, help='CodeGraph.toJson output')
    parser.add_argument('--gold', required=True, type=Path, help='independently labeled JSON array')
    parser.add_argument('--max-examples', type=int, default=15)
    args = parser.parse_args()
    if args.max_examples < 0:
        parser.error('--max-examples must be non-negative')
    report = compare(json.loads(args.graph.read_text(encoding='utf-8')),
                     json.loads(args.gold.read_text(encoding='utf-8')))
    for key in ('falsePositiveExamples', 'falseNegativeExamples'):
        report[key] = report[key][:args.max_examples]
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
