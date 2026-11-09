/* Extracted from templates/guide.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    var PROMPT_URL = '/prompts/keel-tc-rewrite-to-csv.txt';
    var preview = document.getElementById('guide-ai-prompt-preview');
    var copyBtn = document.getElementById('guide-ai-copy-btn');
    var statusEl = document.getElementById('guide-ai-status');
    if (!preview || !copyBtn || !statusEl) {
        return;
    }
    var promptText = '';

    function setStatus(msg, isError) {
        statusEl.textContent = msg || '';
        statusEl.classList.toggle('is-error', !!isError);
    }

    function loadPrompt() {
        return fetch(PROMPT_URL, { credentials: 'same-origin' })
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('Could not load prompt (' + res.status + ')');
                }
                return res.text();
            })
            .then(function (text) {
                promptText = (text || '').trim();
                if (!promptText) {
                    throw new Error('Prompt file was empty');
                }
                preview.textContent = promptText;
                copyBtn.disabled = false;
                setStatus('', false);
            })
            .catch(function (err) {
                promptText = '';
                preview.textContent = 'Prompt failed to load. Open ' + PROMPT_URL + ' in a new tab, or refresh the page.';
                copyBtn.disabled = true;
                setStatus(err && err.message ? err.message : 'Failed to load prompt', true);
            });
    }

    copyBtn.addEventListener('click', function () {
        if (!promptText) {
            setStatus('Prompt not loaded yet', true);
            return;
        }
        var done = function () {
            copyBtn.textContent = 'Copied';
            setStatus('Copied — paste into your AI chat', false);
            window.setTimeout(function () {
                copyBtn.textContent = 'Copy prompt';
            }, 2000);
        };
        var fail = function () {
            setStatus('Clipboard blocked — select the prompt text and press Ctrl+C (Cmd+C on Mac)', true);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(promptText).then(done).catch(fail);
        } else {
            fail();
        }
    });

    loadPrompt();
})();
