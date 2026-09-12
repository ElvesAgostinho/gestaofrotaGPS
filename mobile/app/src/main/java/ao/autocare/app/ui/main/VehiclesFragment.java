package ao.autocare.app.ui.main;

import ao.autocare.app.R;

public class VehiclesFragment extends PlaceholderFragment {

    @Override
    protected int titleRes() {
        return R.string.tab_vehicles;
    }

    @Override
    protected int bodyRes() {
        return R.string.vehicles_coming_soon;
    }

    @Override
    protected int iconRes() {
        return R.drawable.ic_car;
    }
}
