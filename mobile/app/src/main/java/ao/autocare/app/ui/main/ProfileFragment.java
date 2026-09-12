package ao.autocare.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ao.autocare.app.BuildConfig;
import ao.autocare.app.R;
import ao.autocare.app.data.AuthRepository;
import ao.autocare.app.data.SessionManager;
import ao.autocare.app.databinding.FragmentProfileBinding;
import ao.autocare.app.ui.auth.AuthActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AuthRepository auth = new AuthRepository(requireContext());
        SessionManager session = auth.session();

        binding.textName.setText(session.getUserName());
        String contact = session.getUserEmail() != null ? session.getUserEmail() : session.getUserPhone();
        binding.textContact.setText(contact != null ? contact : "—");
        binding.textServer.setText(BuildConfig.API_BASE_URL);

        binding.buttonSignOut.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.sign_out)
                        .setMessage(R.string.sign_out_confirm)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.sign_out, (d, w) -> auth.logout(() -> {
                            startActivity(new Intent(requireContext(), AuthActivity.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            requireActivity().finish();
                        }))
                        .show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
