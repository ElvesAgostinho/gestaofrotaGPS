package ao.autocare.app.ui.main;

import ao.autocare.app.R;

public class AlertsFragment extends PlaceholderFragment {

    @Override
    protected int titleRes() {
        return R.string.tab_alerts;
    }

    @Override
    protected int bodyRes() {
        return R.string.alerts_coming_soon;
    }

    @Override
    protected int iconRes() {
        return R.drawable.ic_bell;
    }
}
