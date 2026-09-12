package ao.autocare.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ao.autocare.app.data.ApiException;
import ao.autocare.app.data.AuthRepository;
import ao.autocare.app.data.api.dto.AuthDtos.RegisterRequest;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;
import ao.autocare.app.databinding.FragmentRegisterBinding;
import ao.autocare.app.ui.onboarding.OnboardingActivity;

public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private AuthRepository auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        auth = new AuthRepository(requireContext());
        binding.buttonGoLogin.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        binding.buttonRegister.setOnClickListener(v -> submit());
    }

    private void submit() {
        String name = value(binding.inputName.getText());
        String email = value(binding.inputEmail.getText());
        String password = value(binding.inputPassword.getText());
        boolean acceptTerms = binding.checkTerms.isChecked();
        hideError();

        if (name.isEmpty()) {
            showError("Indique o seu nome.");
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Indique um email válido.");
            return;
        }
        if (password.length() < 8) {
            showError("A palavra-passe deve ter pelo menos 8 caracteres.");
            return;
        }
        if (!acceptTerms) {
            showError("Precisa de aceitar os termos para continuar.");
            return;
        }

        setLoading(true);
        auth.register(new RegisterRequest(name, email, null, password, true),
                new AuthRepository.Callback<UserDto>() {
                    @Override
                    public void onSuccess(UserDto user) {
                        if (binding == null) return;
                        startActivity(new Intent(requireContext(), OnboardingActivity.class));
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
        binding.buttonRegister.setEnabled(!loading);
        binding.buttonRegister.setText(loading ? "A criar conta…" : "Criar conta");
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
