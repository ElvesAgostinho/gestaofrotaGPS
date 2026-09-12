package ao.autocare.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ao.autocare.app.AutoCareApp;
import ao.autocare.app.R;
import ao.autocare.app.data.api.ApiClient;
import ao.autocare.app.data.api.dto.ConfigDtos.AppConfig;
import ao.autocare.app.databinding.FragmentDashboardBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private static final int[][] SHORTCUTS = {
            {R.string.shortcut_maintenance, R.drawable.ic_wrench},
            {R.string.shortcut_fuel, R.drawable.ic_fuel},
            {R.string.shortcut_expense, R.drawable.ic_cash},
            {R.string.shortcut_document, R.drawable.ic_document},
            {R.string.shortcut_mileage, R.drawable.ic_gauge},
            {R.string.shortcut_trip, R.drawable.ic_route},
    };

    private FragmentDashboardBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String name = AutoCareApp.from(requireContext()).session().getUserName();
        String firstName = name == null || name.isBlank() ? "" : name.split(" ")[0];
        binding.textGreeting.setText(getString(R.string.dashboard_greeting, firstName));
        binding.textTagline.setText(R.string.app_tagline);

        buildShortcuts(inflaterSafe());
        loadConfig();
    }

    private LayoutInflater inflaterSafe() {
        return LayoutInflater.from(requireContext());
    }

    private void buildShortcuts(LayoutInflater inflater) {
        binding.gridShortcuts.removeAllViews();
        for (int[] s : SHORTCUTS) {
            LinearLayout cell = new LinearLayout(requireContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            cell.setPadding(pad, pad, pad, pad);

            androidx.gridlayout.widget.GridLayout.LayoutParams lp =
                    new androidx.gridlayout.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = androidx.gridlayout.widget.GridLayout.spec(
                    androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);
            cell.setLayoutParams(lp);

            ImageView icon = new ImageView(requireContext());
            int size = (int) (24 * getResources().getDisplayMetrics().density);
            icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            icon.setImageResource(s[1]);

            TextView label = new TextView(requireContext());
            label.setText(s[0]);
            label.setTextSize(12);
            label.setGravity(android.view.Gravity.CENTER);

            cell.addView(icon);
            cell.addView(label);
            binding.gridShortcuts.addView(cell);
        }
    }

    private void loadConfig() {
        ApiClient.get(AutoCareApp.from(requireContext()).session())
                .config()
                .enqueue(new Callback<AppConfig>() {
                    @Override
                    public void onResponse(@NonNull Call<AppConfig> call,
                                           @NonNull Response<AppConfig> response) {
                        if (binding != null && response.isSuccessful() && response.body() != null
                                && response.body().tagline != null) {
                            binding.textTagline.setText(response.body().tagline);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AppConfig> call, @NonNull Throwable t) {
                        // mantém a tagline local
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
