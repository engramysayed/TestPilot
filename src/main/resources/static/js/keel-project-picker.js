/* Shared project launcher + pin pattern for job pages. */
(function (global) {
    var API_HEADERS = { 'X-Keel-Requested-With': 'Keel', 'Accept': 'application/json' };

    function projectLabel(p) {
        return (p.name || p.projectId) + ' (' + p.projectId + ')';
    }

    function bindPicker(options) {
        var els = options.elements || {};
        var storageKey = options.storageKey || 'keel.projectId';
        var initialProjectId = options.initialProjectId || '';
        var projects = [];
        var onPinned = options.onPinned || function () {};
        var onShowLauncher = options.onShowLauncher || function () {};

        function showWorkspaceLauncher() {
            if (els.workspaceLauncher) els.workspaceLauncher.hidden = false;
            if (els.workspaceConfig) els.workspaceConfig.hidden = true;
            onShowLauncher();
        }

        function pinWorkspaceProject(projectId, projectList) {
            if (els.projectSel) els.projectSel.value = projectId;
            if (els.launcherSel) els.launcherSel.value = projectId;
            var list = projectList || projects;
            var proj = list.find(function (p) { return p.projectId === projectId; });
            if (els.pinnedName) {
                if (proj) els.pinnedName.textContent = projectLabel(proj);
                else if (els.launcherSel && els.launcherSel.selectedIndex >= 0) {
                    els.pinnedName.textContent = els.launcherSel.options[els.launcherSel.selectedIndex].textContent;
                } else {
                    els.pinnedName.textContent = projectId;
                }
            }
            if (els.workspaceLauncher) els.workspaceLauncher.hidden = true;
            if (els.workspaceConfig) els.workspaceConfig.hidden = false;
            if (els.projectErr) els.projectErr.hidden = true;
            try { sessionStorage.setItem(storageKey, projectId); } catch (e) { /* private mode */ }
            if (options.workflowEl) options.workflowEl.hidden = false;
            onPinned(projectId, list);
        }

        async function loadProjects() {
            if (els.projectErr) els.projectErr.hidden = true;
            if (els.launcherErr) els.launcherErr.hidden = true;
            var res = await fetch('/api/projects', {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (!res.ok) {
                if (els.projectErr) {
                    els.projectErr.hidden = false;
                    els.projectErr.textContent = 'Could not load projects.';
                }
                showWorkspaceLauncher();
                return [];
            }
            var data = await res.json();
            projects = Array.isArray(data) ? data : [];
            if (els.projectSel) els.projectSel.innerHTML = '';
            if (els.launcherSel) {
                els.launcherSel.innerHTML = '';
                var ph = document.createElement('option');
                ph.value = '';
                ph.textContent = '— Select project —';
                els.launcherSel.appendChild(ph);
            }
            if (!projects.length) {
                if (els.noProjects) els.noProjects.hidden = false;
                if (els.launcherNoProjects) els.launcherNoProjects.hidden = false;
                if (options.workflowEl) options.workflowEl.hidden = true;
                showWorkspaceLauncher();
                return projects;
            }
            if (els.noProjects) els.noProjects.hidden = true;
            if (els.launcherNoProjects) els.launcherNoProjects.hidden = true;
            projects.forEach(function (p) {
                var label = projectLabel(p);
                if (els.projectSel) {
                    var opt = document.createElement('option');
                    opt.value = p.projectId;
                    opt.textContent = label;
                    els.projectSel.appendChild(opt);
                }
                if (els.launcherSel) {
                    var lopt = document.createElement('option');
                    lopt.value = p.projectId;
                    lopt.textContent = label;
                    els.launcherSel.appendChild(lopt);
                }
            });
            var urlParams = new URLSearchParams(window.location.search);
            var urlProjectId = urlParams.get('projectId');
            var urlProject = (urlProjectId && projects.some(function (p) { return p.projectId === urlProjectId; }))
                ? urlProjectId
                : ((initialProjectId && projects.some(function (p) { return p.projectId === initialProjectId; }))
                    ? initialProjectId : null);
            var saved = null;
            try { saved = sessionStorage.getItem(storageKey); } catch (e2) { /* */ }
            var match = saved && projects.some(function (p) { return p.projectId === saved; });
            var pinned = urlProject || (match ? saved : null);
            if (pinned) pinWorkspaceProject(pinned, projects);
            else showWorkspaceLauncher();
            return projects;
        }

        if (els.launcherContinue) {
            els.launcherContinue.addEventListener('click', function () {
                if (els.launcherErr) els.launcherErr.hidden = true;
                var projectId = els.launcherSel ? els.launcherSel.value : '';
                if (!projectId) {
                    if (els.launcherErr) {
                        els.launcherErr.hidden = false;
                        els.launcherErr.textContent = 'Select a project to continue.';
                    }
                    return;
                }
                pinWorkspaceProject(projectId, projects);
            });
        }
        if (els.changeProjectBtn) {
            els.changeProjectBtn.addEventListener('click', showWorkspaceLauncher);
        }
        if (els.projectSel && options.onProjectSelectChange) {
            els.projectSel.addEventListener('change', function () {
                options.onProjectSelectChange(els.projectSel.value, projects);
            });
        }

        return {
            loadProjects: loadProjects,
            pinWorkspaceProject: pinWorkspaceProject,
            showWorkspaceLauncher: showWorkspaceLauncher,
            getProjects: function () { return projects.slice(); }
        };
    }

    global.KeelProjectPicker = {
        bind: bindPicker,
        API_HEADERS: API_HEADERS
    };
})(window);