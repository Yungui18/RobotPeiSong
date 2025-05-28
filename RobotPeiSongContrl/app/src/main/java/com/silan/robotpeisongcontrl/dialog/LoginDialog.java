package com.silan.robotpeisongcontrl.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class LoginDialog {
    private final Context context;
    private AlertDialog dialog;

    public LoginDialog(Context context) {
        this.context = context;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_login, null);
        builder.setView(view);

        EditText etUsername = view.findViewById(R.id.et_username);
        EditText etPassword = view.findViewById(R.id.et_password);
        Button btnLogin = view.findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString();
            String password = etPassword.getText().toString();

            // 简单验证
            if ("admin".equals(username) && "123456".equals(password)) {
                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                Toast.makeText(context, "用户名或密码错误", Toast.LENGTH_SHORT).show();
            }
        });

        dialog = builder.create();
        dialog.show();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
