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
            const labelWidth = 310;
            const measureContext = document.createElement('canvas').getContext('2d');
            measureContext.font = '14px Arial, sans-serif';
            const valueWidth = config.showDataLabels
                ? Math.max(120, ...entries.map(item => measureContext.measureText(String(item.value)).width + 16))
                : 12;
            const rows = horizontal ? entries.map(item => {
                const label = [item.label, item.groupLabel].filter(Boolean).join(' ');
                const lines = wrap(measureContext, label, labelWidth - 16);
                return { lines, height: Math.max(66, lines.length * 17 + 24) };
            }) : [];
            const cssWidth = horizontal ? Math.max(1280, padding * 2 + labelWidth + valueWidth + 400)
                : Math.max(980, entries.length * Math.max(132, valueWidth) + padding * 2);
            measureContext.font = '600 22px Arial, sans-serif';
            const titleLines = String(config.title || '').split(/\r?\n/)
                .flatMap(paragraph => wrap(measureContext, paragraph, cssWidth - padding * 2));
            measureContext.font = '13px Arial, sans-serif';
            const detailLines = hasSubtitle
                ? [config.subtitle, config.currency].filter(Boolean)
                    .flatMap(detail => String(detail).split(/\r?\n/))
                    .filter(paragraph => paragraph.trim())
                    .flatMap(paragraph => wrap(measureContext, paragraph, cssWidth - padding * 2))
                : [];
            const extraHeaderHeight = (titleLines.length - 1) * 28 + Math.max(0, detailLines.length - 1) * 18;
            const titleHeight = (hasSubtitle ? 82 : 58) + extraHeaderHeight;
            const laneWidth = (cssWidth - padding * 2) / entries.length;
            measureContext.font = '12px Arial, sans-serif';
            const categoryLines = horizontal ? [] : entries.map(item => wrap(measureContext, item.label, laneWidth - 8));
            const extraLabelHeight = horizontal ? 0
                : Math.max(0, ...categoryLines.map(lines => lines.length - 3)) * 15;
            const cssHeight = horizontal ? Math.max(300, rows.reduce((total, row) => total + row.height, 0) + titleHeight + padding * 2)
                : (hasGroups ? 564 : 520) + extraHeaderHeight + extraLabelHeight;
            const canvas = document.createElement('canvas');
            canvas.width = cssWidth * scale; canvas.height = cssHeight * scale;
            const ctx = canvas.getContext('2d');
            ctx.scale(scale, scale);
            ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, cssWidth, cssHeight);
            ctx.fillStyle = '#153544'; ctx.font = '600 22px Arial, sans-serif'; ctx.textAlign = 'center';
            titleLines.forEach((line, index) => ctx.fillText(line, cssWidth / 2, padding - 14 + index * 28));
            if (detailLines.length) {
                ctx.fillStyle = '#496574'; ctx.font = '13px Arial, sans-serif';
                const detailTop = padding + 10 + (titleLines.length - 1) * 28;
                detailLines.forEach((line, index) => ctx.fillText(line, cssWidth / 2, detailTop + index * 18));
            }
            ctx.strokeStyle = '#d6e0e4'; ctx.lineWidth = 1;
            ctx.fillStyle = '#278DAD';
            if (horizontal) {
                const chartLeft = padding + labelWidth;
                const chartWidth = cssWidth - chartLeft - padding - valueWidth;
                let top = titleHeight + padding;
                entries.forEach((item, index) => {
                    ctx.fillStyle = '#153544'; ctx.font = '14px Arial, sans-serif'; ctx.textAlign = 'left';
                    rows[index].lines.forEach((line, lineIndex) => ctx.fillText(line, padding, top + 24 + lineIndex * 17));
                    ctx.fillStyle = '#e8f0f3'; ctx.fillRect(chartLeft, top + 12, chartWidth, 22);
                    ctx.fillStyle = item.color || '#278DAD'; ctx.fillRect(chartLeft, top + 12, chartWidth * Math.max(0, item.numeric) / max, 22);
                    if (config.showDataLabels) { ctx.fillStyle = '#153544'; ctx.textAlign = 'right'; ctx.fillText(item.value, cssWidth - padding, top + 28); }
                    top += rows[index].height;
                });
            } else {
                const chartTop = titleHeight + padding;
                const chartBottom = cssHeight - padding - (hasGroups ? 112 : 78) - extraLabelHeight;
                const chartHeight = chartBottom - chartTop;
                ctx.beginPath(); ctx.moveTo(padding, chartBottom + .5); ctx.lineTo(cssWidth - padding, chartBottom + .5); ctx.stroke();
                entries.forEach((item, index) => {
                    const barWidth = Math.min(56, laneWidth * .58);
                    const left = padding + index * laneWidth + (laneWidth - barWidth) / 2;
                    const height = chartHeight * Math.max(0, item.numeric) / max;
                    ctx.fillStyle = item.color || '#278DAD'; ctx.fillRect(left, chartBottom - height, barWidth, height);
                    if (config.showDataLabels) { ctx.fillStyle = '#153544'; ctx.font = '13px Arial, sans-serif'; ctx.textAlign = 'center'; ctx.fillText(item.value, left + barWidth / 2, chartBottom - height - 8); }
                    ctx.fillStyle = '#153544'; ctx.font = '12px Arial, sans-serif'; ctx.textAlign = 'center';
                    categoryLines[index].forEach((line, lineIndex) => ctx.fillText(line, left + barWidth / 2, chartBottom + 20 + lineIndex * 15));
                });
                if (hasGroups) {
                    let first = 0;
                    while (first < entries.length) {
                        const year = entries[first].groupLabel || '';
                        let end = first + 1;
                        while (end < entries.length && entries[end].groupLabel === year) end++;
                        const center = padding + ((first + end) / 2) * laneWidth;
                        ctx.fillStyle = '#496574'; ctx.font = '600 12px Arial, sans-serif'; ctx.textAlign = 'center';
                        ctx.fillText(year, center, chartBottom + 86 + extraLabelHeight);
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
