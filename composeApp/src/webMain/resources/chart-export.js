/* First-party PNG export for OMS Compose bar charts. No chart data leaves the browser. */
(() => {
    const wrap = (ctx, value, maxWidth) => {
        const words = String(value || '').split(/\s+/);
        const lines = [];
        let line = '';
        for (const word of words) {
            const next = line ? `${line} ${word}` : word;
            if (line && ctx.measureText(next).width > maxWidth) { lines.push(line); line = word; }
            else line = next;
        }
        if (line) lines.push(line);
        return lines.length ? lines : ['—'];
    };

    const download = (canvas, filename) => {
        const save = blob => {
            if (!blob) throw new Error('PNG encoder returned no image');
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url; anchor.download = filename;
            document.body.append(anchor); anchor.click(); anchor.remove();
            setTimeout(() => URL.revokeObjectURL(url), 60000);
        };
        if (canvas.toBlob) canvas.toBlob(save, 'image/png');
        else {
            const anchor = document.createElement('a');
            anchor.href = canvas.toDataURL('image/png'); anchor.download = filename;
            document.body.append(anchor); anchor.click(); anchor.remove();
        }
    };

    window.downloadOmsChartPng = payloadJson => {
        try {
            const config = JSON.parse(payloadJson);
            if (!Array.isArray(config.data) || config.data.length === 0) return false;
            const horizontal = config.orientation === 'Horizontal';
            const entries = config.data.map(item => ({ ...item, numeric: Number(String(item.value).replace(/[^0-9,.-]/g, '').replace(',', '.')) || 0 }));
            const max = Math.max(1, ...entries.map(item => item.numeric));
            const scale = 2;
            const padding = 52;
            const hasSubtitle = Boolean(config.subtitle || config.currency);
            const hasGroups = entries.some(item => item.groupLabel);
            const titleHeight = hasSubtitle ? 82 : 58;
            const lane = horizontal ? 66 : 150;
            const cssWidth = horizontal ? 1280 : Math.max(980, entries.length * 132 + padding * 2);
            const cssHeight = horizontal ? Math.max(300, entries.length * lane + titleHeight + padding * 2) : (hasGroups ? 564 : 520);
            const canvas = document.createElement('canvas');
            canvas.width = cssWidth * scale; canvas.height = cssHeight * scale;
            const ctx = canvas.getContext('2d');
            ctx.scale(scale, scale);
            ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, cssWidth, cssHeight);
            ctx.fillStyle = '#153544'; ctx.font = '600 22px Arial, sans-serif'; ctx.textAlign = 'center';
            ctx.fillText(config.title, cssWidth / 2, padding - 14);
            if (hasSubtitle) {
                const details = [config.subtitle, config.currency].filter(Boolean).join(' · ');
                ctx.fillStyle = '#496574'; ctx.font = '13px Arial, sans-serif';
                ctx.fillText(details, cssWidth / 2, padding + 10);
            }
            ctx.strokeStyle = '#d6e0e4'; ctx.lineWidth = 1;
            ctx.fillStyle = '#278DAD';
            if (horizontal) {
                const labelWidth = 310;
                const chartLeft = padding + labelWidth;
                const chartWidth = cssWidth - chartLeft - padding - (config.showDataLabels ? 120 : 12);
                entries.forEach((item, index) => {
                    const top = titleHeight + padding + index * lane;
                    ctx.fillStyle = '#153544'; ctx.font = '14px Arial, sans-serif'; ctx.textAlign = 'left';
                    wrap(ctx, item.label, labelWidth - 16).slice(0, 2).forEach((line, lineIndex) => ctx.fillText(line, padding, top + 24 + lineIndex * 17));
                    ctx.fillStyle = '#e8f0f3'; ctx.fillRect(chartLeft, top + 12, chartWidth, 22);
                    ctx.fillStyle = '#278DAD'; ctx.fillRect(chartLeft, top + 12, chartWidth * Math.max(0, item.numeric) / max, 22);
                    if (config.showDataLabels) { ctx.fillStyle = '#153544'; ctx.textAlign = 'right'; ctx.fillText(item.value, cssWidth - padding, top + 28); }
                });
            } else {
                const chartTop = titleHeight + padding;
                const chartBottom = cssHeight - padding - (hasGroups ? 112 : 78);
                const chartHeight = chartBottom - chartTop;
                const laneWidth = (cssWidth - padding * 2) / entries.length;
                ctx.beginPath(); ctx.moveTo(padding, chartBottom + .5); ctx.lineTo(cssWidth - padding, chartBottom + .5); ctx.stroke();
                entries.forEach((item, index) => {
                    const barWidth = Math.min(56, laneWidth * .58);
                    const left = padding + index * laneWidth + (laneWidth - barWidth) / 2;
                    const height = chartHeight * Math.max(0, item.numeric) / max;
                    ctx.fillStyle = '#278DAD'; ctx.fillRect(left, chartBottom - height, barWidth, height);
                    if (config.showDataLabels) { ctx.fillStyle = '#153544'; ctx.font = '13px Arial, sans-serif'; ctx.textAlign = 'center'; ctx.fillText(item.value, left + barWidth / 2, chartBottom - height - 8); }
                    ctx.fillStyle = '#153544'; ctx.font = '12px Arial, sans-serif'; ctx.textAlign = 'center';
                    wrap(ctx, item.label, laneWidth - 8).slice(0, 3).forEach((line, lineIndex) => ctx.fillText(line, left + barWidth / 2, chartBottom + 20 + lineIndex * 15));
                });
                if (hasGroups) {
                    let first = 0;
                    while (first < entries.length) {
                        const year = entries[first].groupLabel || '';
                        let end = first + 1;
                        while (end < entries.length && entries[end].groupLabel === year) end++;
                        const center = padding + ((first + end) / 2) * laneWidth;
                        ctx.fillStyle = '#496574'; ctx.font = '600 12px Arial, sans-serif'; ctx.textAlign = 'center';
                        ctx.fillText(year, center, chartBottom + 86);
                        first = end;
                    }
                }
            }
            download(canvas, config.filename);
            return true;
        } catch (error) {
            console.error('OMS chart PNG export failed', error);
            window.alert?.(JSON.parse(payloadJson)?.exportFailedMessage || 'Export failed');
            return false;
        }
    };
})();
