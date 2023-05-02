package com.ebremer.halcyon.gui;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;

public class LoginForm extends Form {

        private final TextField usernameField;
        private final PasswordTextField passwordField;
        private final Label loginStatus;

        public LoginForm(String id) {
                super(id);
                System.out.println("LogininForm : "+id);
                usernameField = new TextField("username", Model.of(""));
                passwordField = new PasswordTextField("password", Model.of(""));
                loginStatus = new Label("loginStatus", Model.of(""));

                add(usernameField);
                add(passwordField);
                add(loginStatus);
        }

        @Override
        public final void onSubmit() {
                String username = (String)usernameField.getDefaultModelObject();
                String password = (String)passwordField.getDefaultModelObject();
                if(username.equals("test") && password.equals("test"))
                    loginStatus.setDefaultModelObject("Congratulations!");
                else
                    loginStatus.setDefaultModelObject("Wrong username or password!");
        }
}