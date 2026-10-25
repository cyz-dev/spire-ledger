async (page) => {
  const root='E:/Projects/Opensource/塔1求解器/sts1-combat-pilot/state-mod/';
  await page.locator('#file').setInputFiles(root+'runtime/player-review-20261009/bce8e324-e786-41c4-b1db-a79b63e9f83b/run.json');
  await page.waitForFunction(()=>document.querySelector('#timeline > details.floor'));
  const result=await page.evaluate(()=>({facts:stateNotesFacts(run).map(f=>({decision_id:f.decision.decision_id,candidate_id:f.decision.candidate_id,status:f.result.status})),
    floors:document.querySelectorAll('#timeline > details.floor').length,
    collapsed:[...document.querySelectorAll('#timeline > details.floor')].every(n=>!n.open),
    rawIdsInSummaries:[...document.querySelectorAll('#timeline summary')].some(n=>n.textContent.includes('bce8e324'))}));
  if(!result.collapsed||result.rawIdsInSummaries||result.floors<20)throw Error('Floor hierarchy or collapsed anchors missing');
  const floor=page.locator('#timeline > details.floor').filter({hasText:'第 54 层'});
  await floor.evaluate(n=>n.open=true);
  await floor.locator(':scope > details').filter({hasText:'无惧疼痛'}).first().evaluate(n=>n.open=true);
  await page.screenshot({path:root+'evidence-20261010/offline-floor-real.png',fullPage:true});
  await page.locator('#file').setInputFiles(root+'evidence-20261009/malicious.json');
  await page.waitForFunction(()=>document.querySelector('#timeline').textContent.includes('<img'));
  result.safe=await page.evaluate(()=>!window.XSS_EXECUTED&&!document.querySelector('#timeline img,#timeline script'));
  if(!result.safe)throw Error('Untrusted input became active markup');
  return result;
}
