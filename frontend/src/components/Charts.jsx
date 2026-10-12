import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatMoney } from '../utils/format';

// Hex values on purpose: SVG attributes inside Recharts don't reliably resolve CSS variables.
export const PALETTE = ['#0f766e', '#d97706', '#4f46e5', '#be185d', '#65a30d', '#0284c7', '#7c3aed'];
const AXIS = { fontSize: 12, fill: '#4b5b6b' };

const moneyTick = (v) => formatMoney(v);

/** Horizontal bars, one per category. data: [{ category, amount }] */
export function CategoryBarChart({ data }) {
  const height = Math.max(160, data.length * 48 + 40);
  return (
    <div className="chart-box" style={{ height }} role="img" aria-label="Monthly spend by category, bar chart">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 24, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#dfe6e4" />
          <XAxis type="number" tickFormatter={moneyTick} tick={AXIS} />
          <YAxis type="category" dataKey="category" width={104} tick={AXIS} />
          <Tooltip formatter={(v) => [formatMoney(v), 'Per month']} cursor={{ fill: 'rgba(15,118,110,0.08)' }} />
          <Bar dataKey="amount" radius={[0, 4, 4, 0]}>
            {data.map((_, i) => (
              <Cell key={i} fill={PALETTE[i % PALETTE.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Donut by category. data: [{ category, totalSpend }] */
export function CategoryPieChart({ data }) {
  return (
    <div className="chart-box" style={{ height: 300 }} role="img" aria-label="Spend share by category, pie chart">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="totalSpend" nameKey="category" innerRadius={62} outerRadius={104} paddingAngle={2}>
            {data.map((_, i) => (
              <Cell key={i} fill={PALETTE[i % PALETTE.length]} />
            ))}
          </Pie>
          <Tooltip formatter={(v, name) => [formatMoney(v), name]} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
