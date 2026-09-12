package ao.autocare.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import ao.autocare.app.R;

/** Ecrã genérico "Em breve" para módulos de fases seguintes. */
public abstract class PlaceholderFragment extends Fragment {

    @StringRes
    protected abstract int titleRes();

    @StringRes
    protected abstract int bodyRes();

    @DrawableRes
    protected abstract int iconRes();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_placeholder, container, false);
        ((TextView) v.findViewById(R.id.title)).setText(titleRes());
        ((TextView) v.findViewById(R.id.body)).setText(bodyRes());
        ((ImageView) v.findViewById(R.id.image)).setImageResource(iconRes());
        return v;
    }
}
