package com.reveny.virtualinject.ui.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.DialogFragment;

import com.reveny.virtualinject.BuildConfig;
import com.reveny.virtualinject.R;
import com.reveny.virtualinject.databinding.DialogAboutBinding;
import com.reveny.virtualinject.databinding.FragmentHomeBinding;
import com.reveny.virtualinject.ui.dialog.BlurBehindDialogBuilder;
import com.reveny.virtualinject.util.Utility;
import com.reveny.virtualinject.util.chrome.LinkTransformationMethod;
import com.vcore.BlackBoxCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import rikka.material.app.LocaleDelegate;

public class HomeFragment extends BaseFragment {
    private static final String TAG = "VirtualInjectLog";

    private String selectedApp;
    private String libraryPath;

    private FragmentHomeBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.about).setOnMenuItemClickListener(item -> {
            showAbout();
            return true;
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != 1 || resultCode != Activity.RESULT_OK) {
            return;
        }

        if (data == null || data.getData() == null) {
            Toast.makeText(getActivity(), "File selection failed", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = data.getData();

        if (fileUri != null && fileUri.getPath() != null && fileUri.getPath().endsWith(".so")) {
            libraryPath = Objects.requireNonNull(fileUri.getPath()).replace("/document/primary:", Environment.getExternalStorageDirectory().getPath() + "/");
            Toast.makeText(getActivity(), "File Selected: " + libraryPath, Toast.LENGTH_LONG).show();

            // Define destination file in cache directory
            File dest = new File(requireContext().getCacheDir(), "libinject.so");

            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(fileUri);
                 OutputStream outputStream = new FileOutputStream(dest)) {

                // Copy the file content from InputStream to OutputStream
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                Log.i(TAG, "Copied library file to: " + dest.getAbsolutePath());
                binding.libPath.setText(libraryPath);

            } catch (IOException e) {
                Log.e(TAG, "Failed to copy library file", e);
                Toast.makeText(getActivity(), "Failed to copy library file", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            Toast.makeText(getActivity(), "Invalid file type selected. Please select a .so or .dex file.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        setupToolbar(binding.toolbar, null, R.string.app_name, R.menu.menu_home);
        binding.toolbar.setNavigationIcon(null);
        binding.toolbar.setOnClickListener(null);
        binding.appBar.setLiftable(true);
        binding.nestedScrollView.getBorderViewDelegate().setBorderVisibilityChangedListener((top, oldTop, bottom, oldBottom) -> binding.appBar.setLifted(!top));
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        setupApplist();

        binding.libPathChoose.setEndIconOnClickListener(v -> {
            Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
            chooseFile.setType("*/*");

            // For .so
            chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream"});
            chooseFile = Intent.createChooser(chooseFile, "Choose a .so file");
            startActivityForResult(chooseFile, 1);
        });

        binding.installButton.setOnClickListener(v -> {
            if (selectedApp != null) {
                Log.i(TAG, "Installing: " + selectedApp);
                BlackBoxCore.get().installPackageAsUser(selectedApp, 0);

                boolean isInstalled = BlackBoxCore.get().isInstalled(selectedApp, 0);
                Log.i(TAG, "isInstalled: " + isInstalled);
                if (!isInstalled) {
                    Toast.makeText(requireContext(), "Failed to install", Toast.LENGTH_SHORT).show();
                }

                Toast.makeText(requireContext(), "Installed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Please select an app", Toast.LENGTH_SHORT).show();
            }
        });

        binding.launchButton.setOnClickListener(v -> {
            if (selectedApp != null && libraryPath != null) {
                boolean isInstalled = BlackBoxCore.get().isInstalled(selectedApp, 0);
                if (!isInstalled) {
                    Toast.makeText(requireContext(), "Please install the app first", Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.i(TAG, "Launching: " + selectedApp);
                BlackBoxCore.get().launchApk(selectedApp, 0);
            } else {
                Toast.makeText(requireContext(), "Please select a valid app and library path", Toast.LENGTH_SHORT).show();
            }
        });

        return binding.getRoot();
    }

    private void setupApplist() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Utility.getInstalledApps(requireContext())
        );
        binding.appSelectorText.setAdapter(adapter);

        binding.appSelectorText.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            selectedApp = selected;
            Log.i(TAG, "Selected: " + selected);
        });
    }

    public static class AboutDialog extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            DialogAboutBinding binding = DialogAboutBinding.inflate(getLayoutInflater(), null, false);
            setupAboutDialog(binding);
            return new BlurBehindDialogBuilder(requireContext()).setView(binding.getRoot()).create();
        }

        private void setupAboutDialog(DialogAboutBinding binding) {
            binding.designAboutTitle.setText(R.string.app_name);
            binding.designAboutInfo.setMovementMethod(LinkMovementMethod.getInstance());
            binding.designAboutInfo.setTransformationMethod(new LinkTransformationMethod(requireActivity()));
            binding.designAboutInfo.setText(HtmlCompat.fromHtml(getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://t.me/revenyy\">Telegram</a></b>",
                    "<b><a href=\"https://github.com/reveny/\">Reveny</a></b>"), HtmlCompat.FROM_HTML_MODE_LEGACY));
            binding.designAboutVersion.setText(String.format(LocaleDelegate.getDefaultLocale(), "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        }
    }


    private void showAbout() {
        // Showing the About Dialog
        new AboutDialog().show(getChildFragmentManager(), "about");
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}