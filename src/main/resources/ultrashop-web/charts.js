// Chart Manager for UltraShop Dashboard
// Integrates with Chart.js to render premium dark-themed charts and KPI sparklines

const chartFont = { family: 'Outfit, system-ui, sans-serif', size: 12 };
const monoFont = { family: 'JetBrains Mono, monospace', size: 11 };

const tooltipStyle = {
  backgroundColor: 'rgba(10, 10, 26, 0.95)',
  titleColor: '#ffffff',
  bodyColor: '#94a3b8',
  borderColor: 'rgba(255, 255, 255, 0.08)',
  borderWidth: 1,
  borderRadius: 12,
  padding: 12,
  titleFont: { family: 'Outfit, system-ui, sans-serif', size: 13, weight: '600' },
  bodyFont: monoFont,
  displayColors: true,
  boxWidth: 8,
  boxHeight: 8,
  boxPadding: 6,
  usePointStyle: true,
};

function createGradient(ctx, colorTop, colorBottom, height = 300) {
  const g = ctx.createLinearGradient(0, 0, 0, height);
  g.addColorStop(0, colorTop);
  g.addColorStop(1, colorBottom);
  return g;
}

export function renderDailyChart(daily, canvasId, currentChart) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return null;
  const ctx = canvas.getContext('2d');
  
  if (currentChart) {
    currentChart.destroy();
  }

  const revenueGrad = createGradient(ctx, 'rgba(52, 211, 153, 0.22)', 'rgba(52, 211, 153, 0.00)', 300);
  const payoutGrad = createGradient(ctx, 'rgba(251, 113, 133, 0.18)', 'rgba(251, 113, 133, 0.00)', 300);

  return new Chart(ctx, {
    type: 'line',
    data: {
      labels: daily.map(d => d.date),
      datasets: [
        {
          label: 'Revenue',
          data: daily.map(d => +d.revenue),
          borderColor: '#34d399',
          backgroundColor: revenueGrad,
          fill: true,
          tension: 0.4,
          borderWidth: 3,
          pointRadius: 0,
          pointHoverRadius: 6,
          pointHoverBackgroundColor: '#34d399',
          pointHoverBorderColor: '#050510',
          pointHoverBorderWidth: 3,
        },
        {
          label: 'Payouts',
          data: daily.map(d => +d.payout),
          borderColor: '#fb7185',
          backgroundColor: payoutGrad,
          fill: true,
          tension: 0.4,
          borderWidth: 3,
          pointRadius: 0,
          pointHoverRadius: 6,
          pointHoverBackgroundColor: '#fb7185',
          pointHoverBorderColor: '#050510',
          pointHoverBorderWidth: 3,
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      scales: {
        y: {
          beginAtZero: true,
          ticks: {
            color: '#64748b',
            font: monoFont,
            callback: (val) => '$' + parseFloat(val).toLocaleString(),
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.03)',
            drawBorder: false,
          }
        },
        x: {
          ticks: {
            color: '#64748b',
            font: monoFont,
            maxRotation: 45,
            autoSkip: true,
            maxTicksLimit: 12,
          },
          grid: {
            display: false,
          }
        }
      },
      plugins: {
        legend: {
          position: 'top',
          align: 'end',
          labels: {
            color: '#e2e8f0',
            font: chartFont,
            usePointStyle: true,
            pointStyle: 'circle',
            pointStyleWidth: 8,
            padding: 20,
          }
        },
        tooltip: tooltipStyle,
      }
    }
  });
}

export function renderShopChart(shops, canvasId, currentChart) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return null;
  const ctx = canvas.getContext('2d');

  if (currentChart) {
    currentChart.destroy();
  }

  // Curated premium palette: Gold, Emerald, Blue, Rose, Purple, Orange, Cyan, Pink, Lime, Yellow
  const colors = [
    '#f59e0b', '#34d399', '#60a5fa', '#fb7185', '#a78bfa',
    '#fb923c', '#22d3ee', '#f472b6', '#a3e635', '#eab308'
  ];

  return new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: shops.map(s => s.shopId),
      datasets: [{
        data: shops.map(s => +s.revenue),
        backgroundColor: colors.slice(0, shops.length),
        borderColor: '#0a0a1a',
        borderWidth: 2,
        hoverOffset: 6,
        spacing: 3,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '72%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: '#94a3b8',
            padding: 12,
            font: chartFont,
            usePointStyle: true,
            pointStyle: 'circle',
            pointStyleWidth: 8,
          }
        },
        tooltip: tooltipStyle,
      }
    }
  });
}

export function renderSparkline(canvas, data, color) {
  if (!canvas || !data || data.length === 0) return null;
  const ctx = canvas.getContext('2d');

  const gradient = createGradient(ctx, `${color}20`, `${color}00`, 35);

  return new Chart(ctx, {
    type: 'line',
    data: {
      labels: data.map((_, i) => i),
      datasets: [{
        data: data,
        borderColor: color,
        borderWidth: 1.5,
        backgroundColor: gradient,
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 0,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: { display: false },
        y: { display: false }
      },
      plugins: {
        legend: { display: false },
        tooltip: { enabled: false }
      },
      elements: {
        line: {
          capBezierPoints: true
        }
      }
    }
  });
}
