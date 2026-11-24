import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";
import { closePool, login, makeApp, resetDb, seedBranch, seedUser } from "./helpers.js";

after(closePool);
beforeEach(resetDb);

test("login rejects a wrong password and unknown accounts alike", async () => {
  const { app } = await makeApp();
  await seedUser({ email: "owner@example.com", password: "password123" });

  const bad = await app.inject({
    method: "POST",
    url: "/api/v1/auth/login",
    payload: { email: "owner@example.com", password: "wrong-password" },
  });
  assert.equal(bad.statusCode, 401);
  assert.equal(bad.json().error.code, "INVALID_CREDENTIALS");

  const missing = await app.inject({
    method: "POST",
    url: "/api/v1/auth/login",
    payload: { email: "nobody@example.com", password: "password123" },
  });
  assert.equal(missing.statusCode, 401);
  assert.equal(missing.json().error.code, "INVALID_CREDENTIALS");
});

test("login returns tokens and /auth/me reflects branch scope", async () => {
  const { app } = await makeApp();
  const branchId = await seedBranch();
  await seedUser({ email: "owner@example.com", branchIds: [branchId] });
  const { access, user } = await login(app);

  assert.deepEqual(user.branch_ids, [branchId]);
  const me = await app.inject({
    method: "GET",
    url: "/api/v1/auth/me",
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(me.statusCode, 200);
  assert.equal(me.json().user.role, "OWNER");
});

test("refresh rotates the token and reusing the old one revokes the family", async () => {
  const { app } = await makeApp();
  await seedUser({ email: "owner@example.com" });
  const { refresh } = await login(app);

  const first = await app.inject({
    method: "POST",
    url: "/api/v1/auth/refresh",
    payload: { refresh_token: refresh },
  });
  assert.equal(first.statusCode, 200);
  const rotated = first.json().refresh_token as string;
  assert.notEqual(rotated, refresh);

  const reuse = await app.inject({
    method: "POST",
    url: "/api/v1/auth/refresh",
    payload: { refresh_token: refresh },
  });
  assert.equal(reuse.statusCode, 401);
  assert.equal(reuse.json().error.code, "REFRESH_TOKEN_REUSED");

  // The whole family is now revoked, including the freshly rotated token.
  const afterReuse = await app.inject({
    method: "POST",
    url: "/api/v1/auth/refresh",
    payload: { refresh_token: rotated },
  });
  assert.equal(afterReuse.statusCode, 401);
});

test("a MANAGER cannot read another branch", async () => {
  const { app } = await makeApp();
  const mine = await seedBranch("Mine", "MINE");
  const theirs = await seedBranch("Theirs", "THEIRS");
  await seedUser({ email: "manager@example.com", role: "MANAGER", branchIds: [mine] });
  const { access } = await login(app, "manager@example.com");

  const ok = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${mine}/categories`,
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(ok.statusCode, 200);

  const denied = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${theirs}/categories`,
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(denied.statusCode, 403);
  assert.equal(denied.json().error.code, "BRANCH_FORBIDDEN");
});

test("only an OWNER can create users; MANAGER needs a branch", async () => {
  const { app } = await makeApp();
  const branchId = await seedBranch();
  await seedUser({ email: "manager@example.com", role: "MANAGER", branchIds: [branchId] });
  const { access } = await login(app, "manager@example.com");

  const denied = await app.inject({
    method: "POST",
    url: "/api/v1/auth/users",
    headers: { authorization: `Bearer ${access}` },
    payload: { name: "Nope", email: "nope@example.com", password: "password123", role: "MANAGER", branch_ids: [branchId] },
  });
  assert.equal(denied.statusCode, 403);
  assert.equal(denied.json().error.code, "OWNER_ONLY");

  await seedUser({ email: "owner@example.com", role: "OWNER" });
  const owner = await login(app, "owner@example.com");
  const noBranch = await app.inject({
    method: "POST",
    url: "/api/v1/auth/users",
    headers: { authorization: `Bearer ${owner.access}` },
    payload: { name: "Mgr", email: "mgr@example.com", password: "password123", role: "MANAGER", branch_ids: [] },
  });
  assert.equal(noBranch.statusCode, 422);
  assert.equal(noBranch.json().error.code, "BRANCH_REQUIRED");
});
