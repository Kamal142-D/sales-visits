/**
 * Firebase Security Rules emulator tests for VisitFlow company/team enforcement (plan phase 1.5).
 *
 * Run (needs Java on PATH for the Firestore emulator):
 *   cd firestore-tests && npm install
 *   npm test          # → firebase emulators:exec --only firestore "node rules.test.mjs"
 *
 * Each check asserts the BACKEND allows or denies an operation regardless of what a client UI does.
 */
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, writeBatch, setLogLevel,
} from 'firebase/firestore';

setLogLevel('error');

const here = dirname(fileURLToPath(import.meta.url));
const testEnv = await initializeTestEnvironment({
  projectId: 'demo-visitflow',
  firestore: { rules: readFileSync(join(here, '..', 'firestore.rules'), 'utf8') },
});

const CODE = 'ABC123';
const db = (uid) => testEnv.authenticatedContext(uid).firestore();
const anon = () => testEnv.unauthenticatedContext().firestore();

// ---- seed baseline data with rules disabled ----
await testEnv.withSecurityRulesDisabled(async (ctx) => {
  const s = ctx.firestore();
  await setDoc(doc(s, 'companies/co1'), { name: 'Acme', ownerUid: 'mgr', inviteCode: CODE });
  await setDoc(doc(s, 'companies/co1/members/mgr'), { name: 'M', email: 'm@x', role: 'MANAGER' });
  await setDoc(doc(s, 'companies/co1/members/rep'), { name: 'R', email: 'r@x', role: 'REP' });
  await setDoc(doc(s, 'companies/co1/assignments/a1'), { customerName: 'C1', assignedTo: 'rep', done: false });
  await setDoc(doc(s, 'companies/co1/assignments/a2'), { customerName: 'C2', assignedTo: 'mgr', done: false });
  await setDoc(doc(s, 'inviteCodes/ABC123'), { companyId: 'co1' });
  // Shared company records (plan 6.2): one owned by the rep, one by the manager.
  await setDoc(doc(s, 'companies/co1/opportunities/oRep'), { title: 'Rep deal', ownerUid: 'rep' });
  await setDoc(doc(s, 'companies/co1/opportunities/oMgr'), { title: 'Mgr deal', ownerUid: 'mgr' });
  await setDoc(doc(s, 'companies/co1/customers/cuRep'), { name: 'Shared Cust', ownerUid: 'rep' });
  // Records still owned by a user whose membership was already removed (ex-rep), plus their assignment.
  await setDoc(doc(s, 'companies/co1/assignments/aEx'), { customerName: 'Ex', assignedTo: 'exrep', done: false });
  await setDoc(doc(s, 'companies/co1/customers/cuEx'), { name: 'Ex Cust', ownerUid: 'exrep' });
  await setDoc(doc(s, 'companies/co1/opportunities/oEx'), { title: 'Ex deal', ownerUid: 'exrep' });
});

let passed = 0, failed = 0;
async function check(name, promise) {
  try { await promise; console.log(`  ✓ ${name}`); passed++; }
  catch (e) { console.error(`  ✗ ${name}\n      ${e.message}`); failed++; }
}

console.log('VisitFlow Firestore rules — company/team enforcement\n');

// 1) A rep cannot promote themselves to manager.
await check('rep cannot update own role to MANAGER',
  assertFails(updateDoc(doc(db('rep'), 'companies/co1/members/rep'), { role: 'MANAGER' })));

// 2) An outsider cannot inject a self-MANAGER row into a company they do not own.
await check('outsider cannot create a self MANAGER row',
  assertFails(setDoc(doc(db('intruder'), 'companies/co1/members/intruder'),
    { name: 'X', email: 'x@x', role: 'MANAGER' })));

// 3) A non-member cannot read company data.
await check('non-member cannot read the company',
  assertFails(getDoc(doc(db('stranger'), 'companies/co1'))));

// 4) A rep may flip `done` on their OWN assignment.
await check('rep can mark own assignment done',
  assertSucceeds(updateDoc(doc(db('rep'), 'companies/co1/assignments/a1'), { done: true })));

// 5) A rep cannot reassign their assignment to someone else.
await check('rep cannot change assignedTo',
  assertFails(updateDoc(doc(db('rep'), 'companies/co1/assignments/a1'), { assignedTo: 'mgr' })));

// 6) A rep cannot edit an assignment that is not theirs.
await check("rep cannot touch another rep's assignment",
  assertFails(updateDoc(doc(db('rep'), 'companies/co1/assignments/a2'), { done: true })));

// 7) A rep cannot create assignments (manager-only).
await check('rep cannot create an assignment',
  assertFails(setDoc(doc(db('rep'), 'companies/co1/assignments/a3'),
    { customerName: 'C3', assignedTo: 'rep', done: false })));

// 8) Joining requires the company's real invite code.
await check('join with WRONG invite code is denied',
  assertFails(setDoc(doc(db('newrep'), 'companies/co1/members/newrep'),
    { name: 'N', email: 'n@x', role: 'REP', inviteCode: 'WRONG9' })));
await check('join with CORRECT invite code is allowed',
  assertSucceeds(setDoc(doc(db('newrep'), 'companies/co1/members/newrep'),
    { name: 'N', email: 'n@x', role: 'REP', inviteCode: CODE })));

// 9) Founding a NEW company (batch: company + own MANAGER row + invite code) succeeds for the owner.
await check('owner can found a company with a manager row (batch)', assertSucceeds((async () => {
  const s = db('founder');
  const b = writeBatch(s);
  b.set(doc(s, 'companies/co2'), { name: 'New', ownerUid: 'founder', inviteCode: 'NEW999' });
  b.set(doc(s, 'companies/co2/members/founder'), { name: 'F', email: 'f@x', role: 'MANAGER' });
  b.set(doc(s, 'inviteCodes/NEW999'), { companyId: 'co2' });
  return b.commit();
})()));

// 10) Cannot inject a MANAGER row into a company owned by someone else.
await check('cannot self-create MANAGER in a company you do not own',
  assertFails(setDoc(doc(db('imposter'), 'companies/co1/members/imposter'),
    { name: 'I', email: 'i@x', role: 'MANAGER' })));

// 11) Per-account isolation: one user cannot read another user's private subtree.
await check('user cannot read another user private data',
  assertFails(getDoc(doc(db('userA'), 'users/userB/private/state'))));
await check('user can write their OWN private data',
  assertSucceeds(setDoc(doc(db('userA'), 'users/userA/private/state'), { snapshot: '{}' })));

// 12) Only a manager/owner can mint invite codes for a company.
await check('rep cannot mint an invite code',
  assertFails(setDoc(doc(db('rep'), 'inviteCodes/HACK01'), { companyId: 'co1' })));
await check('manager can mint an invite code',
  assertSucceeds(setDoc(doc(db('mgr'), 'inviteCodes/MGR777'), { companyId: 'co1' })));

// 13) Shared company records (plan 6.2) — team scope + owner/manager write matrix.
await check('member (rep) can read a shared company opportunity',
  assertSucceeds(getDoc(doc(db('rep'), 'companies/co1/opportunities/oMgr'))));
await check('non-member cannot read shared company records',
  assertFails(getDoc(doc(db('stranger'), 'companies/co1/opportunities/oRep'))));
await check('rep can edit their OWN shared opportunity',
  assertSucceeds(updateDoc(doc(db('rep'), 'companies/co1/opportunities/oRep'), { title: 'Rep deal v2' })));
await check("rep cannot edit another member's shared opportunity",
  assertFails(updateDoc(doc(db('rep'), 'companies/co1/opportunities/oMgr'), { title: 'hijack' })));
await check('rep cannot reassign ownership of their own record',
  assertFails(updateDoc(doc(db('rep'), 'companies/co1/opportunities/oRep'), { ownerUid: 'mgr' })));
await check('manager can edit any shared opportunity',
  assertSucceeds(updateDoc(doc(db('mgr'), 'companies/co1/opportunities/oRep'), { title: 'mgr edit' })));
await check('member creates a shared record only as its own owner',
  assertSucceeds(setDoc(doc(db('rep'), 'companies/co1/customers/newCu'), { name: 'New', ownerUid: 'rep' })));
await check('member cannot create a shared record owned by someone else',
  assertFails(setDoc(doc(db('rep'), 'companies/co1/customers/spoof'), { name: 'X', ownerUid: 'mgr' })));
await check('rep can delete their own shared customer, not others’',
  assertSucceeds(deleteDoc(doc(db('rep'), 'companies/co1/customers/cuRep'))));

// 14) A REMOVED member (no membership row) keeps no write access to records they still "own".
await check('removed member cannot flip done on their old assignment',
  assertFails(updateDoc(doc(db('exrep'), 'companies/co1/assignments/aEx'), { done: true })));
await check('removed member cannot edit a shared customer they owned',
  assertFails(updateDoc(doc(db('exrep'), 'companies/co1/customers/cuEx'), { name: 'Changed' })));
await check('removed member cannot delete a shared opportunity they owned',
  assertFails(deleteDoc(doc(db('exrep'), 'companies/co1/opportunities/oEx'))));

console.log(`\n${passed} passed, ${failed} failed`);
await testEnv.cleanup();
process.exit(failed === 0 ? 0 : 1);
