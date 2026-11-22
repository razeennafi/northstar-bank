# Unit testing

Run `mvn -f backend/pom.xml test` for JUnit and `npm --prefix frontend test` for Vitest and React Testing Library. Unit tests exclude Docker. Java cases check decimal range and scale, equivalent CAD representations, input rejection, statement dates and spreadsheet-safe CSV cell encoding.

The React tests use controlled API responses to exercise rendering during loading, the sign-in form, safe error feedback, a transfer without a known outcome, and statement links. After a transport error, the retry test checks equality of the original and retried HTTP parameters. These tests cannot establish transaction atomicity, cookie security or proxy compatibility. Use the PostgreSQL integration and Playwright suites for that evidence.
