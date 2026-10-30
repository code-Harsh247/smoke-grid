// Prints the PASSWORD_HASH value for the Worker. The password is typed here and never stored.
// Usage: node scripts/hash-password.mjs   then: npx wrangler secret put PASSWORD_HASH  (paste the line)
import { pbkdf2Sync, randomBytes } from "node:crypto";
import readline from "node:readline";

// Workers' PBKDF2 allows at most 100,000 iterations.
const ITERATIONS = 100_000;

function ask(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    // Hide what is typed.
    rl._writeToOutput = (s) => { if (s.includes(question)) rl.output.write(s); };
    rl.question(question, (answer) => { rl.close(); process.stdout.write("\n"); resolve(answer); });
  });
}

const password = await ask("Password: ");
const again = await ask("Again: ");
if (password !== again) { console.error("Passwords don't match."); process.exit(1); }
if (password.length < 10) { console.error("Use at least 10 characters."); process.exit(1); }

const salt = randomBytes(16);
const hash = pbkdf2Sync(password, salt, ITERATIONS, 32, "sha256");
console.log(`\npbkdf2$${ITERATIONS}$${salt.toString("base64")}$${hash.toString("base64")}`);
