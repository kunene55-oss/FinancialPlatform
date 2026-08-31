// k6's JS runtime (goja) doesn't expose Web Crypto's crypto.randomUUID(),
// so a small RFC4122-shaped v4 generator - the transaction IDs just need
// to parse as valid UUIDs (UUID.fromString on the Java side), not be
// cryptographically random.
export function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
