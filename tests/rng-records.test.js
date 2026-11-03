import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mulberry32, seedFromKey, dailyRng, todayKey, formatKey } from '../src/rng.js';
import { insertRecord } from '../src/records.js';

test('같은 시드는 같은 수열, 다른 날짜는 다른 수열', () => {
  const a = dailyRng('20261103'), b = dailyRng('20261103'), c = dailyRng('20261104');
  const seqA = Array.from({ length: 5 }, () => a()), seqB = Array.from({ length: 5 }, () => b()), seqC = Array.from({ length: 5 }, () => c());
  assert.deepEqual(seqA, seqB);
  assert.notDeepEqual(seqA, seqC);
  for (const v of seqA) assert.ok(v >= 0 && v < 1);
});

test('seedFromKey는 인접 날짜에도 크게 다른 시드를 준다', () => {
  assert.notEqual(seedFromKey('20261103'), seedFromKey('20261104'));
  assert.equal(typeof mulberry32(1)(), 'number');
});

test('todayKey와 formatKey', () => {
  assert.equal(todayKey(new Date(2026, 10, 3)), '20261103');
  assert.equal(formatKey('20261103'), '11월 3일');
});

test('insertRecord는 내림차순 정렬, 10개 유지, 순위 반환', () => {
  let list = [];
  for (let i = 1; i <= 12; i++) ({ list } = insertRecord(list, { score: i * 10, at: i }));
  assert.equal(list.length, 10);
  assert.equal(list[0].score, 120);
  assert.equal(list[9].score, 30);
  const { rank } = insertRecord(list, { score: 5, at: 99 });
  assert.equal(rank, 0); // 10위 밖
  const r2 = insertRecord(list, { score: 125, at: 100 });
  assert.equal(r2.rank, 1);
});
