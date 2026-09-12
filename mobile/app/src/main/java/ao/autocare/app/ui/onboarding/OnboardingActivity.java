package ao.autocare.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import ao.autocare.app.AutoCareApp;
import ao.autocare.app.R;
import ao.autocare.app.databinding.ActivityOnboardingBinding;
import ao.autocare.app.ui.main.MainActivity;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {

    private static final int[][] PAGES = {
            {R.string.onboarding_1_title, R.string.onboarding_1_body, R.drawable.ic_car},
            {R.string.onboarding_2_title, R.string.onboarding_2_body, R.drawable.ic_wrench},
            {R.string.onboarding_3_title, R.string.onboarding_3_body, R.drawable.ic_document},
            {R.string.onboarding_4_title, R.string.onboarding_4_body, R.drawable.ic_pin},
    };

    private ActivityOnboardingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.pager.setAdapter(new PageAdapter());
        new TabLayoutMediator(binding.dots, binding.pager, (tab, position) -> {}).attach();

        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean last = position == PAGES.length - 1;
                binding.buttonPrimary.setText(
                        last ? R.string.onboarding_add_vehicle : R.string.action_continue);
            }
        });

        binding.buttonPrimary.setOnClickListener(v -> {
            int current = binding.pager.getCurrentItem();
            if (current < PAGES.length - 1) {
                binding.pager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });
        binding.buttonSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        AutoCareApp.from(this).session().setSeenOnboarding();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            int[] page = PAGES[position];
            ((android.widget.TextView) holder.itemView.findViewById(R.id.title)).setText(page[0]);
            ((android.widget.TextView) holder.itemView.findViewById(R.id.body)).setText(page[1]);
            ((android.widget.ImageView) holder.itemView.findViewById(R.id.image)).setImageResource(page[2]);
        }

        @Override
        public int getItemCount() {
            return PAGES.length;
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
