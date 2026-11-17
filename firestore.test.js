const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const OUTSIDER_UID = "outsider_uid_003";
const TEAM_CODE = "BPS2026";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("teams").doc(TEAM_CODE).get());
  await assertFails(unauthDb.collection("teams").doc(TEAM_CODE).collection("reports").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacker",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile & joinedTeams: owner isolation and schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Alice creates her own profile (omitting optional nulls)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Budi Santoso",
      email: "budi@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice updates activeTeamId and displayName, preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Budi S.",
      activeTeamId: TEAM_CODE,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read or modify Alice's private profile (PII isolation)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on user profile
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Team creation, joining via Team Code, and role escalation prevention", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Alice creates team BPS2026
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).set({
      teamCode: TEAM_CODE,
      name: "Tim Lapangan BPS",
      locationName: "Perumahan Bumi Papan Selaras",
      ownerId: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice registers herself as ADMIN
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Budi Santoso",
      role: "ADMIN",
      joinedAt: now,
      updatedAt: now,
    })
  );

  // Bob can look up team by exact code
  await assertSucceeds(bobDb.collection("teams").doc(TEAM_CODE).get());

  // Bob cannot list all teams globally
  await assertFails(bobDb.collection("teams").get());

  // Bob cannot self-assign ADMIN role when joining
  await assertFails(
    bobDb.collection("teams").doc(TEAM_CODE).collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Agus Wijaya",
      role: "ADMIN",
      joinedAt: now,
      updatedAt: now,
    })
  );

  // Bob joins validly as MEMBER
  await assertSucceeds(
    bobDb.collection("teams").doc(TEAM_CODE).collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Agus Wijaya",
      role: "MEMBER",
      joinedAt: now,
      updatedAt: now,
    })
  );

  // Bob can update his displayName & photoBase64, but cannot escalate role
  await assertSucceeds(
    bobDb.collection("teams").doc(TEAM_CODE).collection("members").doc(BOB_UID).update({
      displayName: "Agus W.",
      photoBase64: "data_base64_avatar_stub_12345",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("teams").doc(TEAM_CODE).collection("members").doc(BOB_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Daily reports: team isolation, up to 4 photos, partial updates, and author guards", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("teams").doc(TEAM_CODE).set({
      teamCode: TEAM_CODE,
      name: "Tim Lapangan BPS",
      locationName: "Perumahan Bumi Papan Selaras",
      ownerId: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("teams").doc(TEAM_CODE).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Budi",
      role: "ADMIN",
      joinedAt: now,
      updatedAt: now,
    });
    await adminDb.collection("teams").doc(TEAM_CODE).collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Agus",
      role: "MEMBER",
      joinedAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Alice creates a report with 2 photos (no 'id' field in payload since @DocumentId is used)
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("reports").doc("rep_001").set({
      teamId: TEAM_CODE,
      authorId: ALICE_UID,
      memberName: "Budi",
      dayName: "Selasa",
      dateLabel: "17 November 2026",
      timeLabel: "08:00",
      yearMonth: "2026-11",
      dayOfMonth: 17,
      reportTimestampMillis: Date.now() - 3000,
      description: "Patroli gerbang utama dan pengecekan lampu jalan Blok A.",
      photos: ["base64_photo_one_123456", "base64_photo_two_123456"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject more than 4 photos
  await assertFails(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("reports").doc("rep_002").set({
      teamId: TEAM_CODE,
      authorId: ALICE_UID,
      memberName: "Budi",
      dayName: "Selasa",
      dateLabel: "17 November 2026",
      timeLabel: "08:05",
      yearMonth: "2026-11",
      dayOfMonth: 17,
      reportTimestampMillis: Date.now() - 2000,
      description: "Terlalu banyak foto",
      photos: ["1111111111", "2222222222", "3333333333", "4444444444", "5555555555"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob (member) can read & query team reports
  await assertSucceeds(
    bobDb.collection("teams").doc(TEAM_CODE).collection("reports").where("yearMonth", "==", "2026-11").get()
  );

  // Bob cannot edit Alice's report
  await assertFails(
    bobDb.collection("teams").doc(TEAM_CODE).collection("reports").doc("rep_001").update({
      description: "Diubah paksa",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice can update her own report
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("reports").doc("rep_001").update({
      description: "Patroli gerbang utama & Blok A selesai aman.",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Outsider cannot read or write reports
  await assertFails(outsiderDb.collection("teams").doc(TEAM_CODE).collection("reports").get());
  await assertFails(outsiderDb.collection("teams").doc(TEAM_CODE).collection("reports").doc("rep_001").get());
});

test("Live chat messages: text, image, audio voice note, immutability, and team isolation", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("teams").doc(TEAM_CODE).set({
      teamCode: TEAM_CODE,
      name: "Tim Lapangan BPS",
      locationName: "Perumahan Bumi Papan Selaras",
      ownerId: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("teams").doc(TEAM_CODE).collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Budi",
      role: "ADMIN",
      joinedAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Text message
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("messages").doc("msg_1").set({
      teamId: TEAM_CODE,
      senderId: ALICE_UID,
      senderName: "Budi",
      type: "TEXT",
      text: "Selamat pagi tim, situasi gerbang aman.",
      audioDurationSec: 0,
      createdAt: now,
    })
  );

  // Voice note message
  await assertSucceeds(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("messages").doc("msg_2").set({
      teamId: TEAM_CODE,
      senderId: ALICE_UID,
      senderName: "Budi",
      type: "AUDIO",
      text: "",
      audioBase64: "AAAAIGZ0eXBpc29tAAACAGlzb21pc28y",
      audioDurationSec: 8,
      createdAt: now,
    })
  );

  // Empty TEXT message rejected
  await assertFails(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("messages").doc("msg_bad").set({
      teamId: TEAM_CODE,
      senderId: ALICE_UID,
      senderName: "Budi",
      type: "TEXT",
      text: "",
      audioDurationSec: 0,
      createdAt: now,
    })
  );

  // Message update forbidden
  await assertFails(
    aliceDb.collection("teams").doc(TEAM_CODE).collection("messages").doc("msg_1").update({
      text: "Edited",
    })
  );

  // Outsider cannot read chat
  await assertFails(outsiderDb.collection("teams").doc(TEAM_CODE).collection("messages").get());
});
