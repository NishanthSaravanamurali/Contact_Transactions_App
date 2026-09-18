# Importing the Live-Test Collection into Insomnia

The collection file uses Postman Collection v2.1 JSON because Insomnia supports
importing that format directly:

```text
contact-transaction-app-live-tests.postman_collection.json
```

## Import

1. Open Insomnia.
2. Select `Import`.
3. Select `File`.
4. Choose `contact-transaction-app-live-tests.postman_collection.json`.
5. Open the imported `Contact Transaction App - Live Tests` collection.

The import includes collection variables, so you do not need to create an
environment before importing.

## Supply the internal service token

The imported variable contains only this safe placeholder:

```text
PASTE_INTERNAL_SERVICE_TOKEN_HERE
```

After import, open the collection's variables/base-environment editor and replace
the placeholder value of `internalServiceToken` with the same value configured as
`INTERNAL_SERVICE_TOKEN` in User Service.

If your Insomnia version does not show a collection-variable editor, make a
temporary copy of the JSON file outside the Git repository, replace the placeholder
in that copy, and import the copy. Do not put the real token into the tracked file.

## Run

1. Start Discovery Server, User Service, and API Gateway.
2. Run `SETUP-01 Initialize Test Data and Check Gateway Health` once.
3. Run folders in numeric order.
4. Before `AUTH-07`, copy `accessToken`, modify one signature character, and save
   it as `tamperedToken`.
5. Follow the restart instructions in `06 - Expired JWT`.
6. Run `07 - Deactivation` last.
7. Run `08 - Optional Resilience` only when you are ready to stop or reconfigure
   services temporarily.

The initialization request generates unique emails and mobile numbers. Registration
saves `userId`; successful login and password-change login save `accessToken`.

## Before sharing or exporting

Clear these imported collection values:

```text
accessToken
tamperedToken
shortLivedToken
internalServiceToken
```

The detailed expected behavior and Oracle verification queries remain in
[`docs/insomnia-live-test-plan.md`](../docs/insomnia-live-test-plan.md).
