#!/usr/bin/env python3
"""Local CodeWiki Web ReAct -> MCP code-search -> MiniCPM smoke test."""
import argparse
import json
import time
import urllib.request
import uuid
from urllib.parse import urlparse


def request_json(url, payload=None, timeout=5):
    data = None if payload is None else json.dumps(payload).encode('utf8')
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.load(resp)


def symbol_calls(url):
    health = request_json(url + '/actuator/health')
    details = health['components']['enterprise']['details']
    return details.get('toolMetrics', {}).get('codewiki_search_symbol', {}).get('calls', 0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--web-url', default='http://127.0.0.1:18084')
    parser.add_argument('--timeout', type=int, default=55)
    args = parser.parse_args()
    if urlparse(args.web_url).hostname not in ('localhost', '127.0.0.1', '::1'):
        parser.error('Only loopback endpoints are allowed for private repository tests')
    before = symbol_calls(args.web_url)
    req_id = 'mini47-smoke-' + uuid.uuid4().hex[:10]
    payload = {
        'userId': 'codewiki-local-test',
        'sessionId': req_id,
        'requestId': req_id,
        'message': ('Use the registered codewiki_search_symbol tool with query ImpactAnalyzer. '
                    'Then report the exact matching source file path. Do not invent a path.'),
    }
    start = time.monotonic()
    result = request_json(args.web_url + '/api/agent/chat', payload, args.timeout)
    elapsed = round(time.monotonic() - start, 3)
    after = symbol_calls(args.web_url)
    answer = result.get('answer', '')
    report = {
        'elapsed_sec': elapsed,
        'execution_mode': result.get('executionMode'),
        'real_symbol_tool_calls': after - before,
        'answer': answer,
        'evidence_path_present': 'codewiki-indexer/src/main/java/com/agentscope/codewiki/indexer/impact/ImpactAnalyzer.java' in answer,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if report['real_symbol_tool_calls'] < 1 or not report['evidence_path_present']:
        raise SystemExit('FAIL: model did not return a verified tool-backed answer')


if __name__ == '__main__':
    main()
