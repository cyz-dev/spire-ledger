async (page) => {
  const root='E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/evidence-20261009/';
  await page.locator('#file').setInputFiles(root+'fixture-run.json');
  await page.waitForFunction(()=>document.querySelector('#meta').textContent.includes('隔离测试夹具'));
  await page.screenshot({path:root+'review.png',fullPage:true});
  const result=await page.evaluate(()=>({facts:stateNotesFacts(run).map(f=>({decision_id:f.decision.decision_id,candidate_id:f.decision.candidate_id,status:f.result.status})),
    fixtureBanner:document.querySelector('#meta').textContent.includes('不是真人示范'),
    points:document.querySelector('#chart polyline')?.getAttribute('points'),path:document.querySelector('#path').textContent,
    loads:run.records.filter(e=>e.type==='load').map(e=>e.parent_status)}));
  await page.locator('#file').setInputFiles(root+'malicious.json');
  await page.waitForFunction(()=>document.querySelector('#timeline').textContent.includes('<img'));
  result.safe=await page.evaluate(()=>!window.XSS_EXECUTED&&!document.querySelector('#timeline img, #timeline script'));
  return result;
}
