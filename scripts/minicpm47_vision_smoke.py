#!/usr/bin/env python3
"""Synthetic offline PNG -> local MiniCPM-V 4.7 vision projector smoke test."""
import argparse
import base64
import json
import struct
import time
import urllib.request
import zlib
from urllib.parse import urlparse


def png_chunk(tag, content):
    return struct.pack('>I', len(content)) + tag + content + struct.pack('>I', zlib.crc32(tag + content) & 0xffffffff)


def red_png():
    width = height = 64
    rows = b''.join(b'\x00' + bytes([255, 0, 0]) * width for _ in range(height))
    return (b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + png_chunk(b'IDAT', zlib.compress(rows)) + png_chunk(b'IEND', b''))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--url', default='http://127.0.0.1:18086/v1/chat/completions')
    p.add_argument('--model', default='minicpm-v47-full')
    p.add_argument('--timeout', type=int, default=40)
    opts = p.parse_args()
    if urlparse(opts.url).hostname not in {'localhost', '127.0.0.1', '::1'}:
        p.error('Only local model URLs allowed')
    image_url = 'data:image/png;base64,' + base64.b64encode(red_png()).decode('ascii')
    payload = {
        'model': opts.model, 'stream': False, 'max_tokens': 120, 'temperature': 0,
        'messages': [{'role': 'user', 'content': [
            {'type': 'text', 'text': 'What is the main color of this image? Answer one word.'},
            {'type': 'image_url', 'image_url': {'url': image_url}}
        ]}],
    }
    request = urllib.request.Request(opts.url, data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
    begin = time.monotonic()
    with urllib.request.urlopen(request, timeout=opts.timeout) as response:
        result = json.load(response)
    text = result['choices'][0]['message'].get('content', '')
    elapsed = round(time.monotonic() - begin, 3)
    print(json.dumps({'model': result.get('model'), 'elapsed_s': elapsed,
                      'answer': text, 'correct': 'red' in text.lower(),
                      'usage': result.get('usage', {})}, ensure_ascii=False, indent=2))
    if 'red' not in text.lower():
        raise SystemExit('FAIL: model did not identify the solid-red test image')


if __name__ == '__main__':
    main()
