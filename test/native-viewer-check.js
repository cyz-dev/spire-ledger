async (page) => {
  await page.locator('#file').setInputFiles('E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/samples/native-run.json');
  await page.waitForFunction(() => document.querySelector('#timeline').textContent.includes('战后奖励'));
  await page.locator('#timeline > details').filter({hasText:'战后奖励'}).first().evaluate(n=>n.open=true);
  await page.screenshot({path:'E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/samples/native-viewer.png',fullPage:true});
  return await page.evaluate(() => ({facts:stateNotesFacts(run).map(f=>({decision_id:f.decision.decision_id,candidate_id:f.decision.candidate_id,status:f.result.status})),
    points:document.querySelector('#chart polyline')?.getAttribute('points'),
    loads:run.records.filter(e=>e.type==='load').map(e=>({branch_id:e.branch_id,parent_checkpoint_id:e.parent_checkpoint_id,parent_status:e.parent_status})),
    path:document.querySelector('#path').textContent}));
}
