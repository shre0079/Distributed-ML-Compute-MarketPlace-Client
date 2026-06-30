// ── Helpers ──────────────────────────────────────────────────
async function api(url, options) {
    try {
        const res = await fetch(url, options);
        const data = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data };
    } catch (e) {
        return { ok: false, status: 0, data: { message: 'Network error' } };
    }
}

function money(v) {
    if (v == null) return '—';
    return '$' + Number(v).toFixed(8);
}

function duration(ms) {
    if (ms == null) return '—';
    const s = ms / 1000;
    if (s < 60)  return s.toFixed(1) + 's';
    if (s < 3600) return (s / 60).toFixed(1) + 'm';
    return (s / 3600).toFixed(2) + 'h';
}

function pill(status) {
    const cls = (status || '').toLowerCase();
    return `<span class="pill pill--${cls}">${status}</span>`;
}

function timeAgo(epochMs) {
    if (!epochMs) return '—';
    const diff = Date.now() - epochMs;
    const s = Math.floor(diff / 1000);
    if (s < 60)   return s + 's ago';
    if (s < 3600) return Math.floor(s / 60) + 'm ago';
    return Math.floor(s / 3600) + 'h ago';
}

// ── Status card ───────────────────────────────────────────────
let ratesPrefilled = false;

async function refreshStatus() {
    const { ok, data } = await api('/api/status');

    console.log("refreshStatus()", {
        ok,
        data
    });

    console.log({
        online: data.online,
        currentJobStatus: data.currentJobStatus,
        currentJobId: data.currentJobId
    });

    const led      = document.getElementById('led');
    const label    = document.getElementById('statusLabel');
    const badge    = document.getElementById('jobBadge');
    const badgeTxt = document.getElementById('jobBadgeText');

    if (!ok || data.error) {
        led.className  = 'led';
        label.textContent = 'OFFLINE';
        badge.className = 'job-badge';
        badgeTxt.textContent = 'offline';
        return;
    }

    const isRunning = data.currentJobStatus === 'RUNNING';
    const isOnline  = data.online;

    led.className  = 'led' + (isRunning ? ' busy' : isOnline ? ' online' : '');
    label.textContent = isRunning ? 'RUNNING JOB' : isOnline ? 'ONLINE' : 'OFFLINE';

    badge.className = 'job-badge' + (isRunning ? ' running' : '');
    badgeTxt.textContent = data.currentJobId
        ? 'job ' + data.currentJobId.slice(0, 8)
        : 'idle';

    document.getElementById('workerIdLabel').textContent = data.workerId || '—';
    document.getElementById('totalEarned').textContent   = money(data.totalEarned);
    document.getElementById('walletBalance').textContent = money(data.walletBalance);
    document.getElementById('completedJobs').textContent = data.completedJobs ?? 0;

    const rep = data.reputation;
    const repEl = document.getElementById('reputation');
    repEl.textContent = rep != null ? rep.toFixed(2) + ' / 100' : '—';

    document.getElementById('os').textContent       = data.os || '—';
    document.getElementById('cpuCores').textContent = data.cpuCores ?? '—';
    document.getElementById('memoryMB').textContent = data.memoryMB
        ? (data.memoryMB / 1024).toFixed(1) + ' GB'
        : '—';
    document.getElementById('hasGpu').textContent   = data.hasGpu ? '✓ Yes' : '✗ No';

    if (!ratesPrefilled && data.cpuRatePerSecond != null) {
        document.getElementById('cpuRate').value = data.cpuRatePerSecond;
        document.getElementById('gpuRate').value = data.gpuRatePerSecond;
        ratesPrefilled = true;
    }
}

// ── Live log ──────────────────────────────────────────────────
async function refreshLogs() {
    const { ok, data } = await api('/api/logs');
    if (!ok) return;

    const terminal = document.getElementById('terminal');
    const raw = data.logs || '';

    // Colour-code common log prefixes
    const coloured = raw
        .split('\n')
        .map(line => {
            if (line.startsWith('[docker]')) return `<span style="color:#E8A33D">${esc(line)}</span>`;
            if (line.startsWith('[pull]'))   return `<span style="color:#8B949E">${esc(line)}</span>`;
            if (/error|failed|FAILED/i.test(line)) return `<span style="color:#CF4F37">${esc(line)}</span>`;
            if (/success|SUCCESS|registered|Loaded/i.test(line)) return `<span style="color:#3FB9A8">${esc(line)}</span>`;
            return `<span>${esc(line)}</span>`;
        })
        .join('\n');

    terminal.innerHTML = coloured + '\n<span class="terminal-cursor"></span>';

    // Auto-scroll only if already near the bottom
    const atBottom = terminal.scrollHeight - terminal.scrollTop - terminal.clientHeight < 40;
    if (atBottom) terminal.scrollTop = terminal.scrollHeight;
}

function esc(str) {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

// ── Job history ───────────────────────────────────────────────
async function refreshJobs() {
    const { ok, data } = await api('/api/jobs');
    const tbody = document.getElementById('jobHistoryBody');

    if (!ok || !Array.isArray(data) || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="table__empty">No jobs yet — agent is polling for work.</td></tr>';
        return;
    }

    tbody.innerHTML = data.slice(0, 30).map(job => `
    <tr>
      <td class="mono" style="color:var(--muted)">${job.jobId ? job.jobId.slice(0, 8) : '—'}</td>
      <td>${esc(job.dockerImage || '—')}</td>
      <td>${pill(job.status)}</td>
      <td>${pill(job.priority || 'NORMAL')}</td>
      <td class="mono">${duration(job.durationMs)}</td>
      <td class="mono" style="color:var(--amber)">${money(job.workerReward)}</td>
    </tr>
  `).join('');
}

// ── Withdrawals ───────────────────────────────────────────────
async function refreshWithdrawals() {
    const { ok, data } = await api('/api/withdrawals');
    const list = document.getElementById('withdrawList');
    if (!ok || !Array.isArray(data) || data.length === 0) {
        list.innerHTML = '';
        return;
    }

    list.innerHTML = data.slice(0, 8).map(w => `
    <div class="withdraw-item">
      <span class="withdraw-item__amount">${money(w.amount)}</span>
      ${pill(w.status)}
    </div>
  `).join('');
}

// ── Rate form ─────────────────────────────────────────────────
document.getElementById('rateForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const msg = document.getElementById('rateMessage');
    msg.className = 'field__msg';
    msg.textContent = 'Updating...';

    const { ok, data } = await api('/api/rate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            cpuRatePerSecond: Number(document.getElementById('cpuRate').value),
            gpuRatePerSecond: Number(document.getElementById('gpuRate').value)
        })
    });

    msg.className = 'field__msg ' + (ok ? 'ok' : 'err');
    msg.textContent = ok ? '✓ Price updated successfully.' : ('✗ ' + (data.message || 'Could not update price.'));
});

// ── Withdraw form ─────────────────────────────────────────────
document.getElementById('withdrawForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const msg = document.getElementById('withdrawMessage');
    msg.className = 'field__msg';
    msg.textContent = 'Submitting...';

    const { ok, data } = await api('/api/withdraw', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: Number(document.getElementById('withdrawAmount').value) })
    });

    msg.className = 'field__msg ' + (ok ? 'ok' : 'err');
    msg.textContent = ok
        ? '✓ Withdrawal requested — pending admin approval.'
        : ('✗ ' + (data.message || 'Could not submit withdrawal.'));

    if (ok) {
        document.getElementById('withdrawForm').reset();
        refreshWithdrawals();
        refreshStatus();
    }
});

// ── Poll loop ─────────────────────────────────────────────────
refreshStatus();
refreshLogs();
refreshJobs();
refreshWithdrawals();

setInterval(refreshStatus,      4000);
setInterval(refreshLogs,        2000);
setInterval(refreshJobs,       10000);
setInterval(refreshWithdrawals,10000);