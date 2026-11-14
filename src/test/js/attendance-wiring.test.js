import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
test('profile provides an attendance mount and loads the attendance module', () => {
 const html=readFileSync('src/main/resources/static/index.html','utf8');
 const profile=readFileSync('src/main/resources/static/assets/js/profile.js','utf8');
 assert.match(html,/id="profile-attendance"/);
 assert.match(profile,/createAttendance/);
 assert.match(profile,/attendance\.activate/);
 assert.match(profile,/attendance\.clear/);
});
