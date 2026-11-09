/* Extracted from templates/guide.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    function initGuideMotion() {
        var sections = Array.prototype.slice.call(document.querySelectorAll('[data-guide-section]'));
        var toc = document.querySelector('.guide-toc');
        if (!sections.length || !toc) {
            return;
        }

        var tocLinks = toc.querySelectorAll('a[href^="#"]');
        var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        var visible = {};
        var minRatio = 0.15;

        function setActiveSection(id) {
            if (!id) {
                return;
            }
            Array.prototype.forEach.call(tocLinks, function (link) {
                var match = link.getAttribute('href') === '#' + id;
                link.classList.toggle('is-active', match);
                if (match) {
                    link.setAttribute('aria-current', 'location');
                } else {
                    link.removeAttribute('aria-current');
                }
            });
        }

        function updateActiveFromVisible() {
            var activeId = null;
            sections.forEach(function (section) {
                var id = section.getAttribute('data-guide-section');
                if (id && visible[id] >= minRatio) {
                    activeId = id;
                }
            });
            if (activeId) {
                setActiveSection(activeId);
            }
        }

        if (reducedMotion) {
            sections.forEach(function (section) {
                section.classList.add('is-visible');
            });
        }

        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                var id = entry.target.getAttribute('data-guide-section');
                if (!id) {
                    return;
                }
                if (entry.isIntersecting) {
                    if (!reducedMotion) {
                        entry.target.classList.add('is-visible');
                    }
                    visible[id] = entry.intersectionRatio;
                } else {
                    delete visible[id];
                }
            });
            updateActiveFromVisible();
        }, {
            threshold: [0.15, 0.2, 0.35, 0.5, 0.65]
        });

        sections.forEach(function (section) {
            observer.observe(section);
        });

        if (sections[0]) {
            setActiveSection(sections[0].getAttribute('data-guide-section'));
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initGuideMotion);
    } else {
        initGuideMotion();
    }
})();
