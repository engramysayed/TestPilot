# Customer framework core (static TAF)

Vendored from [TAF-Template-CI-CD](https://github.com/engramysayed/TAF-Template-CI-CD).

This tree is the **static** half of every delivery ZIP. TestPilot Delivery copies it into the customer package, then codegen adds **only** `project.pages.*_Locators` / `*_Actions` (and thin generated tests elsewhere — not described here).

Copy `src/main/resources/webapp.properties.example` → `webapp.properties` locally. Never commit filled secrets; delivery packaging must not ship real passwords.

---

## Package map

### `project.drivers` — browser lifecycle

| Class | What it does |
|-------|----------------|
| `WebDriverFactory` | Creates a thread-guarded driver from `BROWSER_TYPE`; facade for element/browser/frame/alert/validation APIs |
| `AbstractDriver` | Shared driver builder hooks |
| `ChromeFactory` / `EdgeFactory` | Browser-specific `WebDriver` construction |
| `Browser` | Enum mapping config → factory |
| `WebDriverProvider` | Contract so listeners can resolve the active `WebDriver` |

### `project.utils.Actions` — Selenium verbs

| Class | What it does |
|-------|----------------|
| `ElementsHandler` | Locate/interact: click, type, clear, getText, select, enabled/displayed |
| `BrowserHandler` | Navigation, current URL, refresh, window sizing |
| `FramesHandler` | Switch to iframe by index/name/locator; return default content |
| `AlertsHandler` | Accept / dismiss / send keys to alerts |

### `project.validations` — assert strategy

| Class | What it does |
|-------|----------------|
| `Assertion` | Hard-assert base used by verification/validation |
| `Validation` | SoftAssert wrapper; `assertAll()` at tear-down |
| `Verification` | Non-blocking checks for optional conditions |

### `project.utils` — cross-cutting

| Class | What it does |
|-------|----------------|
| `dataReader.PropertyReader` | Merge properties files + system props (`BASE_WEB`, credentials keys, waits) |
| `dataReader.JsonReader` | Load JSON fixtures under test resources |
| `WaitHandler` | Explicit wait helpers |
| `TimeManager` | Timestamps / delays for reports |
| `Logs.LogsManager` | Central logging |
| `FilesManager` | File clean/copy helpers |
| `Terminal` | Shell command helper for CI/tools |

### `project.utils.reports` — Allure

| Class | What it does |
|-------|----------------|
| `AllureAttachmentManager` | Attach screenshots/files to steps |
| `AllureEnviromentsManager` | Environment block in report |
| `AllureDownloadManager` / `AllurePaths` / `AllureReportGenerator` | Report paths and generation glue |

### `project.media`

| Class | What it does |
|-------|----------------|
| `ScreenShotManager` | Capture screenshots for evidence |
| `ScreenRecordManager` | Optional session recording |

### `project.listeners`

| Class | What it does |
|-------|----------------|
| `TestNGListeners` | Suite/test hooks wired via `@Listeners` / META-INF services |

### `project.tests` (base only)

| Class | What it does |
|-------|----------------|
| `BaseTest` | `@BeforeSuite` property load; holds `WebDriverFactory` / `JsonReader` for subclasses |

Generated `project.tests.generated` / `todo` classes are produced by Delivery for your Excel cases.
After a conversion, open **`docs/DOMAIN_CATALOG.md`** for the list of generated page classes and tests
(what each page is for, which TC uses which pages). Also see `docs/AUTOMATION_SCORE.md` for pass/TODO counts.

---

## Domain pages (added by Delivery codegen)

After conversion, under `src/main/java/project/pages/`:

| Pattern | Responsibility |
|---------|----------------|
| `{UrlStem}_Locators` | `By` fields with `_Txt_Locator` / `_Btn_Locator` / `_Lnk_Locator` / `_Lbl_Locator` / `_El_Locator` |
| `{UrlStem}_Actions` | Extends Locators; customer-facing steps (`type_Username`, `click_Submit_Button`, soft asserts) |

Stems come from the **live URL** at prove time (e.g. `PracticeTestLogin`, `LoggedInSuccessfully`, `Inventory`, `Cart`).
Maintain locators/actions here as the product UI evolves; keep business flow orchestration in tests.

**Per-delivery inventory:** `docs/DOMAIN_CATALOG.md` (written at emit time — not in this static template alone).

Templates used to generate them live in `templates/PageLocators.java.ftl` and `templates/PageActions.java.ftl`.

### How to run generated tests

```bash
mvn clean test
```

Copy `src/main/resources/webapp.properties.example` → `webapp.properties` and set `BASE_WEB` (and credentials if needed).
