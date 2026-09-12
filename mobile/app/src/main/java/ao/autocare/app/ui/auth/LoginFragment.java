package ao.autocare.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ao.autocare.app.data.ApiException;
import ao.autocare.app.data.AuthRepository;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;
import ao.autocare.app.databinding.FragmentLoginBinding;
import ao.autocare.app.ui.main.MainActivity;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthRepository auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        auth = new AuthRepository(requireContext());

        binding.buttonGoRegister.setOnClickListener(v ->
                ((AuthActivity) requireActivity()).show(new RegisterFragment(), true));

        binding.buttonLogin.setOnClickListener(v -> submit());
    }

    private void submit() {
        String identifier = value(binding.inputIdentifier.getText());
        String password = value(binding.inputPassword.getText());
        hideError();

        if (identifier.isEmpty() || password.isEmpty()) {
            showError("Preencha o email/telefone e a palavra-passe.");
            return;
        }

        setLoading(true);
        auth.login(identifier, password, new AuthRepository.Callback<UserDto>() {
            @Override
            public void onSuccess(UserDto user) {
                if (binding == null) return;
                startActivity(new Intent(requireContext(), MainActivity.class));
                requireActivity().finish();
            }

            @Override
            public void onError(ApiException error) {
                if (binding == null) return;
                setLoading(false);
                showError(error.getMessage());
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.buttonLogin.setEnabled(!loading);
        binding.buttonLogin.setText(loading ? "A entrar…" : "Entrar");
    }

    private void showError(String message) {
        binding.textError.setText(message);
        binding.textError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.textError.setVisibility(View.GONE);
    }

    private static String value(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
