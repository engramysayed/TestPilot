# Controlled local acceptance — results

**Label:** controlled local fixture. **Not** representative third-party validation. Launch **HOLD**. Candidate `2721e6d` unchanged. No commit.

Shop: `http://127.0.0.1:4943`

| Mode | passed | todo | message |
|---|---|---|---|
| A DOM NEW | 9 | 4 | ok |
| A DOM UPDATE | 9 | 4 | ok |
| B UI-TARS | 0 | 1 | ok |
| C Precision key unset | 2 | 0 | ok |

Vision actually invoked: **true**

Cursor execution: **not tested** (key unset). No credentials recorded.

| Artifact | SHA-256 |
|---|---|
| DOM NEW zip | `83c6f1cd47b963f795fd11eda026fc207e7fa9aa678af8030839726d420a24f8` |
| DOM UPDATE zip | `23928de3acc0ff6a2c3a9fd75b4cad014a766eb3173889f8236a68148b88b33a` |

NEW replay exit=0 {Jacket_medium_blue=PASS, Login=PASS, Create_account=PASS, Checkout_required_address=PASS, Duplicate_register=PASS, Invalid_login=PASS, Remove_second_remove=PASS, Change_tote_quantity=PASS, Add_two_products=PASS}

UPDATE replay exit=0 {Jacket_medium_blue=PASS, Login=PASS, Create_account=PASS, Checkout_required_address=PASS, Duplicate_register=PASS, Invalid_login=PASS, Remove_second_remove=PASS, Change_tote_quantity=PASS, Add_two_products=PASS}

IR snapshot:

```
TC_ACC_01 REUSED 
TC_SES_01 TODO 
TC_VAR_01 REUSED 
TC_CHK_02 PARTIAL 
TC_ORD_01 TODO 
TC_CHK_01 PASSED 
TC_VIS_01 PARTIAL 
TC_CART_02 PASSED 
TC_CART_01 PASSED 
TC_CART_03 PASSED 
TC_ACC_04 REUSED 
TC_ACC_02 REUSED 
TC_ACC_03 REUSED 
```

## Limitations

- Controlled local fixture only. Not representative third-party application validation.
- TYPE_PASS still maps to `${TARGET_PASSWORD}` unless the Excel step quotes a literal; pack uses quoted synthetic passwords.
- Generated ZIP replay does not emit Excel `Open /path` navigations; login form is on the home page so replay can type without that step.
- Identical repeated `Remove` labels are not ordinal-bound; tote uses `Remove tote`.
- ConversionJobRunner proves Excel order without re-expanding Call-before; dependents must be a contiguous chain or they start a fresh browser.
- `DELIVERY_POST_ACTION_WAIT_MS=2000` in this JVM only (delayed errors are 1200–1500ms).
- Launch remains **HOLD**. No commit, publish, candidate replace, or gate close.

See `D:/priv/testpilot/keel-local-acceptance/evidence/run-log.txt`.
