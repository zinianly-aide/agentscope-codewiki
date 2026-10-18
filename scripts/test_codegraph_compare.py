#!/usr/bin/env python3
"""Run: python3 -m unittest scripts/test_codegraph_compare.py"""
import unittest
from scripts.codegraph_compare import compare


class GraphEvaluationTest(unittest.TestCase):
    def test_counts_false_positive_and_missing_calls(self):
        graph = {
            'nodes': [
                {'id': 'a', 'filePath': 'src/A.java', 'startLine': 2},
                {'id': 'b', 'filePath': 'src/B.java', 'startLine': 5},
                {'id': 'c', 'filePath': 'src/C.java', 'startLine': 9},
            ],
            'edges': [{'type': 'CALLS', 'sourceId': 'a', 'targetId': 'b'},
                      {'type': 'CALLS', 'sourceId': 'a', 'targetId': 'c'}],
        }
        gold = [{'callerPath': 'src/A.java', 'callerDeclarationLine': 2,
                 'targetPath': 'src/B.java', 'targetDeclarationLine': 5},
                {'callerPath': 'src/B.java', 'callerDeclarationLine': 5,
                 'targetPath': 'src/C.java', 'targetDeclarationLine': 9}]
        report = compare(graph, gold)
        self.assertEqual((1, 1, 1), (report['truePositives'], report['falsePositives'],
                                     report['falseNegatives']))
        self.assertEqual(0.5, report['precision'])
        self.assertEqual(0.5, report['recall'])
        self.assertEqual(0.5, report['f1'])

    def test_rejects_incomplete_gold(self):
        with self.assertRaises(ValueError):
            compare({'nodes': [], 'edges': []}, [{'callerPath': 'x'}])


if __name__ == '__main__':
    unittest.main()
