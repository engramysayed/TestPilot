Investigate execution failure for {{testCaseId}} step {{intentIndex}} ({{failureClass}}).

Read-only analysis; do not weaken expected results.

See execution-investigation-evidence.json for the failing step. When irDir is set
there, read prior steps' IR, screenshots and HTML from that directory to tell
"step N failed because step M did X" vs "step N is a real app regression".
