package spireledger;
import basemod.TopPanelItem;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;

/** BaseMod's existing top-panel group provides a reachable entry during a run. */
final class HistoryTopPanelItem extends TopPanelItem {
    HistoryTopPanelItem(){super(ImageMaster.loadImage("images/relics/blueCandle.png"),"spire-ledger:history");}
    @Override protected void onClick(){HistoryScreen.open();}
    @Override protected void onHover(){super.onHover();TipHelper.renderGenericTip(getHitbox().x-320*Settings.scale,Settings.HEIGHT-120*Settings.scale,HistoryScreen.tr("尖塔账本","Spire Ledger"),HistoryScreen.tr("查看当前局与历史记录。打开后暂停局内更新，返回原屏幕。","View current and past runs. Pauses the run and returns to the same screen."));}
}
