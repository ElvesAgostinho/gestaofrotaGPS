package ao.autocare.app.ui.main;

import ao.autocare.app.R;

/**
 * Mapa / GPS em tempo real — Fase 7. Enquanto não houver um provedor GPS
 * configurado, mostra "GPS não configurado" (regra #78: nada de ligações falsas).
 */
public class MapFragment extends PlaceholderFragment {

    @Override
    protected int titleRes() {
        return R.string.gps_not_configured;
    }

    @Override
    protected int bodyRes() {
        return R.string.gps_not_configured_body;
    }

    @Override
    protected int iconRes() {
        return R.drawable.ic_pin;
    }
}
