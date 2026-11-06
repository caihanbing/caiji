# Project instructions

## User-visible UI changes

For every future addition or modification to the app's user-visible interface, first prepare a high-fidelity design mockup and receive the user's explicit confirmation. A text description or wireframe alone does not satisfy this requirement. Do not change UI code until the mockup has been approved. This applies to page structure, navigation, layout, colors, typography, iconography, wording, and interaction affordances. Approval covers only the design the user reviewed; ask again before implementing a materially different visual design.

## Task delivery

After completing each requested development task, work in the current workspace without creating a new workspace or branch. Build a new versioned release APK, verify its package, version, signature and SHA-256, commit the completed source and documentation changes, and push directly to GitHub `main` without force pushing. For every new app version, also create a GitHub Release with a matching `v<version>` tag and attach the verified release APK and its SHA-256 sidecar. Never include release signing keys or passwords in Git.

When a user explicitly requests task completion, generate commit and GitHub Release text with the `commit-msg` skill and proceed with the authorized commit, push to `main`, and Release creation without requesting a second confirmation.

For future releases, use `dms-commit-msg` to generate both the commit message and Release notes. Keep each Release title in the format `财记 <version>`, for example `财记 0.5.0`; proceed with the authorized push and Release without asking again.
