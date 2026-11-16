(function () {
    var NO_FILE = 'No file chosen';
    var CHOOSE = 'Choose file';
    var CHOOSE_ANOTHER = 'Change file';

    function enhance(input) {
        if (!input || input.type !== 'file') return;
        if (input.hasAttribute('hidden') || input.hidden) return;
        if (input.getAttribute('data-keel-file-skip') === 'true') return;
        if (input.closest('[data-keel-file-ready]')) return;

        var wrap = document.createElement('div');
        wrap.className = 'keel-file-wrap';
        wrap.setAttribute('data-keel-file-ready', '1');

        var parent = input.parentNode;
        parent.insertBefore(wrap, input);
        wrap.appendChild(input);
        input.classList.add('keel-file-native');
        input.setAttribute('aria-label', input.getAttribute('aria-label') || 'File upload');

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'button ghost keel-file-choose';
        btn.textContent = CHOOSE;

        var nameEl = document.createElement('span');
        nameEl.className = 'keel-file-chosen muted';
        nameEl.textContent = NO_FILE;

        wrap.appendChild(btn);
        wrap.appendChild(nameEl);

        function sync() {
            var file = input.files && input.files[0];
            if (file) {
                nameEl.textContent = file.name;
                nameEl.classList.add('has-file');
                btn.textContent = CHOOSE_ANOTHER;
            } else {
                nameEl.textContent = NO_FILE;
                nameEl.classList.remove('has-file');
                btn.textContent = CHOOSE;
            }
        }

        btn.addEventListener('click', function (e) {
            e.preventDefault();
            input.click();
        });
        input.addEventListener('change', sync);
        sync();
    }

    function enhanceAll(root) {
        (root || document).querySelectorAll('input[type="file"]').forEach(enhance);
    }

    document.documentElement.setAttribute('lang', 'en');
    document.documentElement.setAttribute('dir', 'ltr');

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { enhanceAll(document); });
    } else {
        enhanceAll(document);
    }

    window.KeelFileInput = { enhance: enhance, enhanceAll: enhanceAll };
})();