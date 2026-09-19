# Smart Expense Tracker — Cloud APK Build

This project is prepared to build the Android APK in GitHub Actions, so Android Studio is not required on your old PC.

## What you need

- A free GitHub account
- A web browser
- Your Android phone

## First-time setup

1. Create a new GitHub repository. A private repository is recommended because this app concerns financial data.
2. Upload the contents of this project to the repository (not the ZIP file itself).
3. Open the repository's **Actions** tab.
4. Select **Build Android APK**.
5. Click **Run workflow**.
6. Wait for the green check mark.
7. Open the completed workflow run.
8. Under **Artifacts**, download **SmartExpenseTracker-debug-apk**.
9. Extract the downloaded artifact and copy `app-debug.apk` to your Android phone.
10. On the phone, open the APK and allow installation from your browser/file manager if Android asks.

## Automatic builds

The workflow also runs automatically whenever you push changes to the `main` branch.

## Important

This produces a **debug APK for testing**, not a Google Play release package. Android signs debug APKs automatically for testing. Keep the repository private if you do not want the source code publicly visible.
