# MobileCodeWriter — real types (M4-02)

**Implemented:** `delivery.mobile.codegen.MobileCodeWriter` in `patches/mobile/M4-02/mobile-codegen/`.  
**Consumes:** AE `delivery.mobile.ir.MobileCaseIr` via `MobileIrCodec` only — never ProvenStepCodec / web IR.

```java
package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import java.nio.file.Path;
import java.util.List;

public final class MobileCodeWriter {
  public void writeVerified(Path outputRoot, List<MobileCaseIr> cases, NamingMap names) throws Exception;
}
```

Emit: one `Screen.java.ftl` per `screenKey` → `project.screens.{Stem}Screen`.  
Thin `GeneratedMobileTest` / `TodoMobileTest`. Dump/blank stems demoted to TODO (`MobileCodegenEmitGate`).  
Platform dispatch (M4-04): `MobileEmitDispatch.acceptsPlatform("ANDROID")`.
