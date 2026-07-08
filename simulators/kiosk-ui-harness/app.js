// simulators/kiosk-ui-harness/app.js
/**
 * Kiosk UI Simulator — Data-driven UI harness for POS product profiles.
 * 
 * Manages 5 screen states: welcome, basket, payment, receipt, assist.
 * Loads Phase-12 profile JSONs and toggles UI elements accordingly.
 */

(function() {
    'use strict';

    // ============================================================
    // State
    // ============================================================
    const state = {
        profile: null,
        screen: 'welcome',
        basket: [],
        subtotal: 0.0,
        selectedPaymentMethod: null,
        assistActive: false,
        assistMessage: '',
        transactionComplete: false
    };

    // ============================================================
    // DOM References
    // ============================================================
    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const dom = {
        profileSelect: $('#profile-select'),
        loadBtn: $('#load-profile-btn'),
        resetBtn: $('#reset-btn'),
        profileName: $('#profile-name'),

        screens: {
            welcome: $('#screen-welcome'),
            basket: $('#screen-basket'),
            payment: $('#screen-payment'),
            receipt: $('#screen-receipt'),
            assist: $('#screen-assist')
        },

        welcome: {
            title: $('#welcome-title'),
            subtitle: $('#welcome-subtitle'),
            startBtn: $('#welcome-start-btn')
        },

        basket: {
            items: $('#basket-items'),
            itemCount: $('#item-count'),
            subtotal: $('#basket-subtotal'),
            payBtn: $('#basket-pay-btn'),
            scanBtn: $('#basket-scan-btn'),
            assistBtn: $('#basket-assist-btn'),
            indicators: $('#peripheral-indicators')
        },

        payment: {
            total: $('#payment-total'),
            methods: $('#payment-methods'),
            authorizeBtn: $('#payment-authorize-btn'),
            cancelBtn: $('#payment-cancel-btn'),
            status: $('#payment-status')
        },

        receipt: {
            title: $('#receipt-title'),
            details: $('#receipt-details'),
            newBtn: $('#receipt-new-btn')
        },

        assist: {
            title: $('#assist-title'),
            message: $('#assist-message'),
            details: $('#assist-details'),
            dismissBtn: $('#assist-dismiss-btn')
        },

        footer: {
            mode: $('#footer-mode'),
            peripherals: $('#footer-peripherals'),
            screen: $('#footer-screen')
        }
    };

    // ============================================================
    // Profile Loading
    // ============================================================
    const PROFILE_DIR = 'profiles/';
    const PROFILE_FILES = [
        'elera.json',
        'mxp-vision-kiosk.json',
        'mxp-smart-hybrid.json',
        'mxp-smart-wall.json',
        'mxp-smart-wing.json',
        'self-checkout-system-7.json',
        'tcx-display.json',
        'tcx-printer-single.json',
        'tcx-printer-dual.json'
    ];

    function loadProfileList() {
        const select = dom.profileSelect;
        select.innerHTML = '<option value="">— Select —</option>';
        PROFILE_FILES.forEach(function(file) {
            const name = file.replace('.json', '').replace(/-/g, ' ').replace(/\b\w/g, function(l) { return l.toUpperCase(); });
            const opt = document.createElement('option');
            opt.value = file;
            opt.textContent = name;
            select.appendChild(opt);
        });
    }

    function loadProfile(file) {
        const url = PROFILE_DIR + file;
        fetch(url)
            .then(function(res) {
                if (!res.ok) throw new Error('Profile not found: ' + file);
                return res.json();
            })
            .then(function(profile) {
                state.profile = profile;
                dom.profileName.textContent = profile.name || file;
                applyProfile(profile);
                showScreen('welcome');
                updateFooter();
                console.log('✅ Loaded profile:', profile.name);
            })
            .catch(function(err) {
                console.error('Failed to load profile:', err);
                alert('Could not load profile: ' + file + '\n' + err.message);
            });
    }

    // ============================================================
    // Profile Application
    // ============================================================
    function applyProfile(profile) {
        // Update payment methods
        const methods = profile.capabilities?.payment?.methods || ['card'];
        renderPaymentMethods(methods);

        // Update peripheral indicators
        renderPeripheralIndicators(profile.capabilities?.peripherals || {});

        // Update welcome screen
        const display = profile.capabilities?.display || {};
        dom.welcome.title.textContent = 'Welcome to ' + (profile.name || 'Self Checkout');
        dom.welcome.subtitle.textContent = display.size ? 
            display.size + '" ' + (display.orientation || '') + ' display' :
            'Scan your first item to begin';

        // Update footer
        dom.footer.mode.textContent = 'Profile: ' + (profile.name || 'None');

        // Show/hide assist button based on profile capabilities
        const hasAssist = profile.capabilities?.hooks?.attended_override?.enabled || false;
        dom.basket.assistBtn.style.display = hasAssist ? 'inline-flex' : 'none';
    }

    function renderPaymentMethods(methods) {
        const container = dom.payment.methods;
        const methodIcons = {
            card: '💳',
            mobile_wallet: '📱',
            nfc: '📶',
            biometric: '👆',
            cash: '💵',
            gift_card: '🎁'
        };
        container.innerHTML = '';
        methods.forEach(function(method) {
            const div = document.createElement('div');
            div.className = 'payment-method';
            div.dataset.method = method;
            div.innerHTML = '<span class="method-icon">' + (methodIcons[method] || '💳') + '</span>' +
                           method.replace(/_/g, ' ').replace(/\b\w/g, function(l) { return l.toUpperCase(); });
            div.addEventListener('click', function() {
                selectPaymentMethod(method);
            });
            container.appendChild(div);
        });
    }

    function renderPeripheralIndicators(peripherals) {
        const container = dom.basket.indicators;
        container.innerHTML = '';
        const labels = {
            scanner: '🖨️ Scanner',
            scale: '⚖️ Scale',
            printer: '🖨️ Printer',
            pin_pad: '🔢 PIN Pad',
            bagging: '🛍️ Bagging',
            camera: '📷 Camera',
            nfc_reader: '📶 NFC'
        };
        for (const [key, value] of Object.entries(peripherals)) {
            const enabled = value === true || value?.enabled === true;
            const div = document.createElement('span');
            div.className = 'peripheral-indicator ' + (enabled ? 'active' : 'inactive');
            div.textContent = labels[key] || key;
            container.appendChild(div);
        }
        if (container.children.length === 0) {
            container.innerHTML = '<span class="peripheral-indicator">No peripherals defined</span>';
        }
    }

    // ============================================================
    // Screen Management
    // ============================================================
    function showScreen(screenName) {
        // Hide all screens
        $$('.screen').forEach(function(el) {
            el.classList.remove('active');
        });

        // Show target screen
        const target = dom.screens[screenName];
        if (target) {
            target.classList.add('active');
            state.screen = screenName;
            dom.footer.screen.textContent = 'Screen: ' + screenName;

            // Special handling
            if (screenName === 'basket') updateBasket();
            if (screenName === 'payment') updatePayment();
            if (screenName === 'receipt') updateReceipt();
        }
    }

    // ============================================================
    // Basket Management
    // ============================================================
    function addItem(sku, price, name) {
        const existing = state.basket.find(function(item) { return item.sku === sku; });
        if (existing) {
            existing.quantity += 1;
        } else {
            state.basket.push({ sku: sku, name: name || sku, price: price || 0.99, quantity: 1 });
        }
        recalcBasket();
        showScreen('basket');
    }

    function recalcBasket() {
        let total = 0;
        let count = 0;
        state.basket.forEach(function(item) {
            total += item.price * item.quantity;
            count += item.quantity;
        });
        state.subtotal = total;
        dom.basket.itemCount.textContent = count + ' items';
        dom.basket.subtotal.textContent = '$' + total.toFixed(2);
        updateBasket();
    }

    function updateBasket() {
        const container = dom.basket.items;
        if (state.basket.length === 0) {
            container.innerHTML = '<p class="empty-message">No items scanned yet</p>';
            return;
        }
        container.innerHTML = '';
        state.basket.forEach(function(item) {
            const div = document.createElement('div');
            div.className = 'basket-item';
            div.innerHTML = '<span class="item-name">' + item.name + ' x' + item.quantity + '</span>' +
                           '<span class="item-price">$' + (item.price * item.quantity).toFixed(2) + '</span>';
            container.appendChild(div);
        });
    }

    // ============================================================
    // Payment
    // ============================================================
    function selectPaymentMethod(method) {
        state.selectedPaymentMethod = method;
        $$('.payment-method').forEach(function(el) {
            el.classList.toggle('selected', el.dataset.method === method);
        });
    }

    function updatePayment() {
        dom.payment.total.textContent = '$' + state.subtotal.toFixed(2);
        dom.payment.status.className = 'payment-status';
        dom.payment.status.style.display = 'none';
        dom.payment.status.textContent = '';
    }

    function authorizePayment() {
        const method = state.selectedPaymentMethod;
        if (!method) {
            dom.payment.status.className = 'payment-status failure';
            dom.payment.status.style.display = 'block';
            dom.payment.status.textContent = 'Please select a payment method.';
            return;
        }

        // Mock payment authorization
        const success = Math.random() > 0.15; // 85% success
        if (success) {
            dom.payment.status.className = 'payment-status success';
            dom.payment.status.style.display = 'block';
            dom.payment.status.textContent = '✅ Payment approved!';
            state.transactionComplete = true;
            setTimeout(function() {
                showScreen('receipt');
            }, 800);
        } else {
            dom.payment.status.className = 'payment-status failure';
            dom.payment.status.style.display = 'block';
            dom.payment.status.textContent = '❌ Payment declined. Please try again.';
        }
    }

    // ============================================================
    // Receipt
    // ============================================================
    function updateReceipt() {
        dom.receipt.details.textContent = '';
        const lines = [];
        lines.push('='.repeat(40));
        lines.push('  RECEIPT');
        lines.push('='.repeat(40));
        state.basket.forEach(function(item) {
            lines.push('  ' + item.name + ' x' + item.quantity + '  $' + (item.price * item.quantity).toFixed(2));
        });
        lines.push('-'.repeat(40));
        lines.push('  TOTAL: $' + state.subtotal.toFixed(2));
        lines.push('='.repeat(40));
        lines.push('  Thank you for your purchase!');
        dom.receipt.details.textContent = lines.join('\n');
        dom.receipt.title.textContent = state.transactionComplete ? 'Transaction Complete!' : 'Transaction in progress...';
    }

    // ============================================================
    // Assist / Exception Overlay
    // ============================================================
    function showAssist(message, details) {
        state.assistActive = true;
        dom.assist.message.textContent = message || 'Please wait for a store associate.';
        if (details) {
            dom.assist.details.innerHTML = '';
            Object.entries(details).forEach(function([key, value]) {
                const row = document.createElement('div');
                row.className = 'detail-row';
                row.innerHTML = '<span>' + key + '</span><span>' + value + '</span>';
                dom.assist.details.appendChild(row);
            });
        } else {
            dom.assist.details.innerHTML = '<p>No additional details available.</p>';
        }
        showScreen('assist');
    }

    function dismissAssist() {
        state.assistActive = false;
        showScreen('basket');
    }

    // ============================================================
    // Footer
    // ============================================================
    function updateFooter() {
        const profile = state.profile;
        if (profile) {
            dom.footer.peripherals.textContent = 'Peripherals: ' + 
                Object.keys(profile.capabilities?.peripherals || {}).join(', ') || '—';
        }
    }

    // ============================================================
    // Reset
    // ============================================================
    function resetAll() {
        state.basket = [];
        state.subtotal = 0.0;
        state.selectedPaymentMethod = null;
        state.transactionComplete = false;
        state.assistActive = false;
        dom.payment.status.className = 'payment-status';
        dom.payment.status.style.display = 'none';
        $$('.payment-method').forEach(function(el) {
            el.classList.remove('selected');
        });
        recalcBasket();
        showScreen('welcome');
    }

    // ============================================================
    // Event Bindings
    // ============================================================
    function initEvents() {
        // Profile loading
        dom.loadBtn.addEventListener('click', function() {
            const file = dom.profileSelect.value;
            if (file) loadProfile(file);
        });

        dom.resetBtn.addEventListener('click', resetAll);

        // Welcome screen
        dom.welcome.startBtn.addEventListener('click', function() {
            if (!state.profile) {
                alert('Please load a product profile first.');
                return;
            }
            showScreen('basket');
        });

        // Basket screen
        dom.basket.payBtn.addEventListener('click', function() {
            if (state.basket.length === 0) {
                alert('Basket is empty. Please add items first.');
                return;
            }
            showScreen('payment');
        });

        dom.basket.scanBtn.addEventListener('click', function() {
            // Mock scan: add a random item
            const skus = ['SKU-1001', 'SKU-1002', 'SKU-1003', 'SKU-1004', 'SKU-1005'];
            const names = ['Milk (1 gal)', 'Bread (white)', 'Eggs (dozen)', 'Chicken Breast (lb)', 'Apple (each)'];
            const idx = Math.floor(Math.random() * skus.length);
            addItem(skus[idx], (Math.random() * 5 + 1).toFixed(2), names[idx]);
        });

        dom.basket.assistBtn.addEventListener('click', function() {
            showAssist('Assistance requested.', { reason: 'Customer pressed assist button' });
        });

        // Payment screen
        dom.payment.authorizeBtn.addEventListener('click', authorizePayment);
        dom.payment.cancelBtn.addEventListener('click', function() {
            showScreen('basket');
        });

        // Receipt screen
        dom.receipt.newBtn.addEventListener('click', resetAll);

        // Assist screen
        dom.assist.dismissBtn.addEventListener('click', dismissAssist);

        // Keyboard shortcuts (for testing)
        document.addEventListener('keydown', function(e) {
            if (e.key === 'r' || e.key === 'R') resetAll();
            if (e.key === 'Escape' && state.assistActive) dismissAssist();
        });
    }

    // ============================================================
    // Init
    // ============================================================
    function init() {
        loadProfileList();
        resetAll();
        initEvents();
        console.log('✅ Kiosk UI Simulator initialized');
        console.log('📋 Select a profile from the dropdown and click "Load" to begin.');
    }

    // Run on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();