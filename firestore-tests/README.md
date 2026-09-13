# VisitFlow — Firestore rules tests (plan phase 1.5)

Backend-enforcement tests for `firestore.rules`, run against the Firebase **Firestore emulator**
(no real project touched — uses a throwaway `demo-visitflow` project id).

## What they prove

The rules enforce company/team access in the backend, not by hiding UI:

- a rep **cannot** promote themselves to manager (nor inject a manager row into a company they don't own);
- joining a company **requires the company's current invite code**;
- only a manager/owner can mint invite codes, create/delete assignments, or change roles;
- a rep may change **only the `done` flag** on **their own** assignment — never reassign it or touch another rep's;
- a non-member cannot read a company; a user cannot read another user's `users/{uid}` subtree.

## Run

Requires **Node** and a **JDK 21+** on `PATH` (the Firestore emulator needs it — note this is a
*different* JDK from the Android build, which uses JDK 17).

```bash
cd firestore-tests
npm install
npm test          # boots the emulator, runs rules.test.mjs, prints PASS/FAIL, exits non-zero on failure
```

Last run: **15 passed, 0 failed** (Firestore emulator v1.22.0).

## Deploying the rules (done by the project owner)

The tests do **not** deploy anything. To make the rules live on the real project `visitflow-3f533`:

```bash
firebase deploy --only firestore:rules
```

(Also enable Email/Password sign-in in Firebase Auth if not already on.)
