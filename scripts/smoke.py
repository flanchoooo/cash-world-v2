#!/usr/bin/env python3
"""Add one complete agent purchase/reversal scenario to a development API."""
import json
import os
import urllib.request
import uuid
from decimal import Decimal

base = os.environ.get('BASE_URL', 'http://localhost:8080')
token = None


def call(method, path, body=None, financial=False):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if financial:
        headers['Idempotency-Key'] = str(uuid.uuid4())
    request = urllib.request.Request(base + path, None if body is None else json.dumps(body).encode(), headers, method=method)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


token = call('POST', '/api/auth/login', {
    'username': os.environ.get('ADMIN_USERNAME', 'admin'),
    'password': os.environ.get('ADMIN_PASSWORD', 'local-admin-change-me'),
})['accessToken']
customer = call('POST', '/api/customers/individuals', {
    'firstName': 'Development', 'lastName': 'Agent', 'mobileNumber': '263771000001',
})
call('POST', '/api/customers/' + customer['customerNumber'] + '/make-agent', {'agentType': 'STANDARD'})
wallet = call('POST', '/api/wallets', {'customerId': customer['id'], 'currency': 'USD', 'name': 'Smoke test USD'})
number = wallet['walletNumber']
call('POST', '/api/wallets/deposit', {'walletNumber': number, 'amount': '100.00'}, True)
paid = call('POST', '/api/bill-payments', {'walletNumber': number, 'productCode': 'ZETDC_USD', 'customerReference': '123456789', 'amount': '20.00'}, True)
assert Decimal(paid['commissionAmount']) == Decimal('0.40'), paid
assert Decimal(call('GET', '/api/wallets/' + number)['balance']) == Decimal('80.40')
reversed_payment = call('POST', '/api/transactions/' + paid['transactionReference'] + '/reverse', {'reason': 'Development smoke test'}, True)
assert len(reversed_payment['entries']) == 2, reversed_payment
assert Decimal(call('GET', '/api/wallets/' + number)['balance']) == Decimal('100.00')
print('PASS: USD 100.00 -> USD 80.40 -> USD 100.00; wallet ' + number)
