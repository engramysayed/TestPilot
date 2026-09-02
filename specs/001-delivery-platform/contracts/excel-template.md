# Contract: Manual TC Excel Template

## File

- Format: `.xlsx`
- First sheet used
- Row 1 = headers (case-insensitive match to names below)
- Data from row 2+

## Columns

| Header | Required | Description |
|--------|----------|-------------|
| TC_ID | yes | Stable unique id within file |
| Title | yes | Human title |
| Preconditions | no | Setup notes |
| Steps | yes | Numbered manual steps text |
| ExpectedResult | yes | Expected outcomes → assertions |
| Priority | no | Ordering hint |
| Tags | no | Comma-separated tags |

## Rejection rules

- Missing any required header → `INVALID_EXCEL`
- Blank `TC_ID` on any data row → `INVALID_EXCEL`
- Duplicate `TC_ID` → `INVALID_EXCEL`
- Zero data rows → `INVALID_EXCEL` or empty-job policy documented as reject for MVP

## Content hash (Update)

`contentHash = hash(normalize(Steps) + "|" + normalize(ExpectedResult))`  
Unchanged `TC_ID` + hash → reuse stored automation (no AI).
