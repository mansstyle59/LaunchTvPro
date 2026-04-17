/**
 * TVNav - Moteur de navigation TV D-pad pour Android WebView
 * Transforme n'importe quel site web en navigation télécommande compatible
 */
(function() {
    'use strict';

    if (window.__TVNavLoaded) return;
    window.__TVNavLoaded = true;

    /* ─── Configuration ─── */
    const CONFIG = {
        FOCUS_COLOR: '#00D4FF',
        FOCUS_BG: 'rgba(0, 212, 255, 0.15)',
        FOCUS_OUTLINE: '3px solid #00D4FF',
        FOCUS_SHADOW: '0 0 0 3px rgba(0,212,255,0.4), 0 0 20px rgba(0,212,255,0.3)',
        SCROLL_MARGIN: 120,
        SELECTOR: [
            'a[href]', 'button', 'input:not([type="hidden"])',
            'select', 'textarea', '[tabindex]:not([tabindex="-1"])',
            '[role="button"]', '[role="link"]', '[role="menuitem"]',
            '[role="tab"]', '[role="option"]', '[onclick]',
            'summary', 'label[for]', '[role="checkbox"]',
            '[role="radio"]', 'video[controls]', 'audio[controls]'
        ].join(','),
        LONGPRESS_MS: 600,
        SCROLL_SPEED: 300,
    };

    /* ─── State ─── */
    let currentEl = null;
    let styleEl = null;
    let longPressTimer = null;
    let isInputMode = false;

    /* ─── Inject CSS ─── */
    function injectStyles() {
        styleEl = document.createElement('style');
        styleEl.id = '__tvnav_styles';
        styleEl.textContent = `
            .__tvnav_focus {
                outline: ${CONFIG.FOCUS_OUTLINE} !important;
                outline-offset: 3px !important;
                box-shadow: ${CONFIG.FOCUS_SHADOW} !important;
                background-color: ${CONFIG.FOCUS_BG} !important;
                transition: outline 0.1s ease, box-shadow 0.1s ease !important;
                border-radius: 4px !important;
                position: relative !important;
                z-index: 9999 !important;
            }
            .__tvnav_cursor {
                position: fixed;
                width: 10px; height: 10px;
                background: ${CONFIG.FOCUS_COLOR};
                border-radius: 50%;
                pointer-events: none;
                z-index: 99999;
                transition: all 0.15s ease;
                box-shadow: 0 0 10px ${CONFIG.FOCUS_COLOR};
                opacity: 0;
            }
            .__tvnav_tooltip {
                position: fixed;
                bottom: 20px; left: 50%;
                transform: translateX(-50%);
                background: rgba(0,0,0,0.85);
                color: #fff;
                padding: 6px 14px;
                border-radius: 20px;
                font-size: 13px;
                font-family: sans-serif;
                pointer-events: none;
                z-index: 99999;
                border: 1px solid rgba(0,212,255,0.4);
                backdrop-filter: blur(4px);
                transition: opacity 0.3s;
                opacity: 0;
            }
        `;
        document.head.appendChild(styleEl);
    }

    /* ─── Get all focusable & visible elements ─── */
    function getFocusable() {
        const els = Array.from(document.querySelectorAll(CONFIG.SELECTOR));
        return els.filter(el => {
            if (!el.offsetParent && el.tagName !== 'BODY') return false;
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) return false;
            const style = window.getComputedStyle(el);
            if (style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') return false;
            return true;
        });
    }

    /* ─── Set focus ─── */
    function setFocus(el) {
        if (!el) return;
        if (currentEl) {
            currentEl.classList.remove('__tvnav_focus');
            currentEl.blur && currentEl.blur();
        }
        currentEl = el;
        currentEl.classList.add('__tvnav_focus');
        currentEl.focus && currentEl.focus({ preventScroll: true });
        scrollIntoViewIfNeeded(el);
        updateTooltip(el);

        // Notify Android
        if (window.TVBridge) {
            window.TVBridge.onFocusChanged(el.tagName, el.href || el.value || el.textContent.trim().substring(0, 50));
        }
    }

    /* ─── Smooth scroll to element ─── */
    function scrollIntoViewIfNeeded(el) {
        const r = el.getBoundingClientRect();
        const vw = window.innerWidth, vh = window.innerHeight;
        const margin = CONFIG.SCROLL_MARGIN;
        let scrollX = 0, scrollY = 0;

        if (r.top < margin) scrollY = r.top - margin;
        else if (r.bottom > vh - margin) scrollY = r.bottom - vh + margin;
        if (r.left < margin) scrollX = r.left - margin;
        else if (r.right > vw - margin) scrollX = r.right - vw + margin;

        if (scrollX !== 0 || scrollY !== 0) {
            window.scrollBy({ left: scrollX, top: scrollY, behavior: 'smooth' });
        }
    }

    /* ─── Tooltip overlay ─── */
    let tooltipEl = null;
    let tooltipTimeout = null;
    function updateTooltip(el) {
        if (!tooltipEl) {
            tooltipEl = document.createElement('div');
            tooltipEl.className = '__tvnav_tooltip';
            document.body.appendChild(tooltipEl);
        }
        clearTimeout(tooltipTimeout);
        const label = el.getAttribute('aria-label') || el.getAttribute('title') || el.textContent.trim().substring(0, 60) || el.tagName;
        const tag = el.tagName.toLowerCase();
        let icon = '→';
        if (tag === 'input') icon = '✏️';
        else if (tag === 'button' || el.getAttribute('role') === 'button') icon = '▶';
        else if (tag === 'a') icon = '🔗';
        else if (tag === 'select') icon = '▼';

        tooltipEl.textContent = `${icon} ${label}`;
        tooltipEl.style.opacity = '1';
        tooltipTimeout = setTimeout(() => { tooltipEl.style.opacity = '0'; }, 2500);
    }

    /* ─── Spatial navigation algorithm ─── */
    function getCenter(r) {
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }

    function getDirectionScore(from, to, dir) {
        const fc = getCenter(from);
        const tc = getCenter(to);
        const dx = tc.x - fc.x;
        const dy = tc.y - fc.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist === 0) return -1;

        let angle = 0;
        switch (dir) {
            case 'right': angle = Math.atan2(dy, dx); break;
            case 'left':  angle = Math.atan2(dy, -dx); break;
            case 'down':  angle = Math.atan2(dx, dy); break;
            case 'up':    angle = Math.atan2(dx, -dy); break;
        }

        // Must be within 90° cone
        if (angle > Math.PI / 2 || angle < -Math.PI / 2) return -1;

        // Primary direction bias
        const axialPenalty = Math.abs(angle) * 2;
        return dist + axialPenalty * dist;
    }

    function navigate(dir) {
        const focusable = getFocusable();
        if (focusable.length === 0) return;

        if (!currentEl || !currentEl.classList.contains('__tvnav_focus')) {
            const visibleEls = focusable.filter(el => {
                const r = el.getBoundingClientRect();
                return r.top >= 0 && r.bottom <= window.innerHeight;
            });
            setFocus(visibleEls[0] || focusable[0]);
            return;
        }

        const fromRect = currentEl.getBoundingClientRect();
        let best = null;
        let bestScore = Infinity;

        for (const el of focusable) {
            if (el === currentEl) continue;
            const toRect = el.getBoundingClientRect();
            const score = getDirectionScore(fromRect, toRect, dir);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                best = el;
            }
        }

        if (best) {
            setFocus(best);
            return true;
        }

        // Scroll page if no element found
        const scrollMap = {
            'right': [150, 0], 'left': [-150, 0],
            'down': [0, 200], 'up': [0, -200]
        };
        window.scrollBy({ left: scrollMap[dir][0], top: scrollMap[dir][1], behavior: 'smooth' });

        // Re-try after scroll
        setTimeout(() => {
            const focusable2 = getFocusable();
            const fromRect2 = currentEl ? currentEl.getBoundingClientRect() : { left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 };
            let best2 = null, bestScore2 = Infinity;
            for (const el of focusable2) {
                if (el === currentEl) continue;
                const score = getDirectionScore(fromRect2, el.getBoundingClientRect(), dir);
                if (score >= 0 && score < bestScore2) { bestScore2 = score; best2 = el; }
            }
            if (best2) setFocus(best2);
        }, 350);
    }

    /* ─── Activate (Enter/OK) ─── */
    function activate() {
        if (!currentEl) return;
        const tag = currentEl.tagName.toLowerCase();

        if (isInputMode) {
            exitInputMode();
            return;
        }

        if (tag === 'input' && (currentEl.type === 'text' || currentEl.type === 'search' || currentEl.type === 'url' || currentEl.type === 'email' || currentEl.type === 'number' || currentEl.type === 'password' || currentEl.type === 'tel')) {
            enterInputMode();
            return;
        }

        if (tag === 'input' && (currentEl.type === 'checkbox' || currentEl.type === 'radio')) {
            currentEl.click();
            return;
        }

        if (tag === 'select') {
            currentEl.focus();
            // Simulate opening
            const event = new KeyboardEvent('keydown', { key: ' ', bubbles: true });
            currentEl.dispatchEvent(event);
            return;
        }

        if (tag === 'a' && currentEl.href) {
            currentEl.click();
            return;
        }

        currentEl.click();
        currentEl.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    }

    /* ─── Input mode ─── */
    function enterInputMode() {
        isInputMode = true;
        currentEl.focus();
        if (window.TVBridge) window.TVBridge.onInputModeChanged(true);
        showToast('Mode saisie — OK pour terminer');
    }

    function exitInputMode() {
        isInputMode = false;
        if (window.TVBridge) window.TVBridge.onInputModeChanged(false);
        showToast('Navigation D-pad activée');
    }

    function showToast(msg) {
        if (!tooltipEl) return;
        tooltipEl.textContent = msg;
        tooltipEl.style.opacity = '1';
        clearTimeout(tooltipTimeout);
        tooltipTimeout = setTimeout(() => { tooltipEl.style.opacity = '0'; }, 2500);
    }

    /* ─── Keyboard handler ─── */
    function handleKey(e) {
        if (isInputMode) {
            if (e.key === 'Escape' || e.keyCode === 4) { // 4 = Android Back
                e.preventDefault();
                exitInputMode();
            }
            return;
        }

        const keyMap = {
            'ArrowRight': 'right', 'ArrowLeft': 'left',
            'ArrowDown': 'down', 'ArrowUp': 'up',
            39: 'right', 37: 'left', 40: 'down', 38: 'up',
            // DPAD keycodes
            22: 'right', 21: 'left', 20: 'down', 19: 'up',
        };

        const dir = keyMap[e.key] || keyMap[e.keyCode];
        if (dir) {
            e.preventDefault();
            e.stopPropagation();
            navigate(dir);
            return;
        }

        if (e.key === 'Enter' || e.keyCode === 23 || e.keyCode === 66) {
            e.preventDefault();
            activate();
            return;
        }

        // Page scroll shortcuts
        if (e.keyCode === 33) { // Page Up
            window.scrollBy({ top: -window.innerHeight * 0.8, behavior: 'smooth' });
        } else if (e.keyCode === 34) { // Page Down
            window.scrollBy({ top: window.innerHeight * 0.8, behavior: 'smooth' });
        }
    }

    /* ─── Mouse/touch hover → focus ─── */
    function handleMouseOver(e) {
        if (isInputMode) return;
        const target = e.target.closest(CONFIG.SELECTOR);
        if (target && target !== currentEl) {
            setFocus(target);
        }
    }

    /* ─── Public API for Android bridge ─── */
    window.TVNav = {
        navigate,
        activate,
        focusFirst: () => {
            const focusable = getFocusable();
            if (focusable.length > 0) setFocus(focusable[0]);
        },
        focusNextInput: () => {
            const inputs = Array.from(document.querySelectorAll('input:not([type="hidden"]), select, textarea')).filter(el => el.offsetParent);
            const idx = inputs.indexOf(currentEl);
            if (idx >= 0 && idx < inputs.length - 1) setFocus(inputs[idx + 1]);
            else if (inputs.length > 0) setFocus(inputs[0]);
        },
        refresh: () => { currentEl = null; },
        scrollUp: () => window.scrollBy({ top: -CONFIG.SCROLL_SPEED, behavior: 'smooth' }),
        scrollDown: () => window.scrollBy({ top: CONFIG.SCROLL_SPEED, behavior: 'smooth' }),
        isInputMode: () => isInputMode,
        showToast,
        exitInputMode,
    };

    /* ─── Init ─── */
    function init() {
        injectStyles();
        document.addEventListener('keydown', handleKey, true);
        document.addEventListener('mouseover', handleMouseOver, { passive: true });

        // Auto-focus first element after page load
        setTimeout(() => {
            const focusable = getFocusable();
            if (focusable.length > 0) setFocus(focusable[0]);
        }, 800);

        // Re-init on SPA navigation
        const observer = new MutationObserver(() => {
            if (!currentEl || !document.contains(currentEl)) {
                currentEl = null;
                setTimeout(() => {
                    if (!currentEl) {
                        const els = getFocusable();
                        if (els.length > 0) setFocus(els[0]);
                    }
                }, 600);
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });

        console.log('[TVNav] Moteur de navigation TV chargé ✓');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
