async (page) => {
  await page.locator('#file').setInputFiles('E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/samples/run.json');
  await page.waitForFunction(() => document.querySelector('#timeline details'));
  const facts = await page.evaluate(() => stateNotesFacts(run).map(f => ({decision_id:f.decision.decision_id,candidate_id:f.decision.candidate_id,status:f.result.status})));
  console.log(JSON.stringify({status:await page.locator('#status').textContent(),facts}));
  await page.screenshot({path:'E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/samples/viewer.png',fullPage:true});
  await page.locator('#file').setInputFiles('E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/samples/malicious.json');
  await page.waitForFunction(() => document.querySelector('#timeline').textContent.includes('<img'));
  const safe = await page.evaluate(() => !window.XSS_EXECUTED && document.querySelectorAll('#timeline img, #timeline script').length === 0);
  if (!safe) throw new Error('Imported text executed or became HTML');
  console.log('VIEWER_XSS_TEXT_PASS');
  return {facts,safe,status:await page.locator('#status').textContent()};
}
