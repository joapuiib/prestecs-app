package com.fpmislata.prestecs.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpmislata.prestecs.R
import com.fpmislata.prestecs.core.config.Environment
import com.fpmislata.prestecs.ui.components.label
import com.fpmislata.prestecs.ui.components.message
import com.fpmislata.prestecs.ui.theme.PrestecsTheme

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onEnvironmentChange = viewModel::onEnvironmentChange,
        onSubmit = viewModel::onSubmit,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEnvironmentChange: (Environment) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.canSwitchEnvironment) {
                EnvironmentPicker(
                    selected = state.environment,
                    enabled = !state.isLoading,
                    onSelect = onEnvironmentChange,
                )
            }

            if (state.needsCredentials) {
                CredentialFields(state, onUsernameChange, onPasswordChange, onSubmit)
            } else {
                Text(stringResource(R.string.login_local_hint))
            }

            state.error?.let { error ->
                Text(
                    text = error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
        }
    }
}

@Composable
private fun CredentialFields(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Username },
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        enabled = !state.isLoading,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvironmentPicker(selected: Environment, enabled: Boolean, onSelect: (Environment) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.login_environment)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Environment.entries.forEach { environment ->
                DropdownMenuItem(
                    text = { Text(environment.label()) },
                    onClick = {
                        expanded = false
                        onSelect(environment)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginError.message(): String = when (this) {
    LoginError.MissingFields -> stringResource(R.string.login_error_missing_fields)

    LoginError.InvalidCredentials -> stringResource(R.string.login_error_invalid_credentials)

    is LoginError.Moodle ->
        message
            ?.let { stringResource(R.string.login_error_moodle, it) }
            ?: stringResource(R.string.error_unexpected)

    is LoginError.Api -> error.message()
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    PrestecsTheme {
        LoginContent(
            state = LoginUiState(
                environment = Environment.STAGING,
                canSwitchEnvironment = true,
                username = "nom.cognom",
                error = LoginError.InvalidCredentials,
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onEnvironmentChange = {},
            onSubmit = {},
        )
    }
}
