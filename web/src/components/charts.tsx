import {
	PieChart,
	Pie,
	Cell,
	BarChart,
	Bar,
	XAxis,
	YAxis,
	Tooltip,
	ResponsiveContainer
} from 'recharts';

export type Slice = { name: string; value: number; color: string };

const AXIS_TICK = { fontSize: 12, fill: 'var(--text-muted)', fontFamily: 'var(--font-ui)' };

/** Donut with a large center figure and a compact legend underneath. */
export function DonutChart({
	data,
	centerValue,
	centerLabel
}: {
	data: Slice[];
	centerValue: string | number;
	centerLabel: string;
}) {
	return (
		<>
			<div className="chart-box">
				<ResponsiveContainer width="100%" height="100%">
					<PieChart>
						<Pie
							data={data}
							dataKey="value"
							nameKey="name"
							innerRadius="64%"
							outerRadius="92%"
							paddingAngle={data.length > 1 ? 2 : 0}
							stroke="none"
							startAngle={90}
							endAngle={-270}
						>
							{data.map((d) => (
								<Cell key={d.name} fill={d.color} />
							))}
						</Pie>
						<Tooltip />
					</PieChart>
				</ResponsiveContainer>
				<div className="donut-center">
					<div className="dc-value">{centerValue}</div>
					<div className="dc-label">{centerLabel}</div>
				</div>
			</div>
			<div className="chart-legend">
				{data.map((d) => (
					<span className="cl-item" key={d.name}>
						<span className="cl-dot" style={{ background: d.color }} />
						{d.name}
						<span className="cl-val">{d.value}</span>
					</span>
				))}
			</div>
		</>
	);
}

/** Horizontal bars for ranked categories (e.g. deviations by type). */
export function CategoryBars({ data }: { data: Slice[] }) {
	return (
		<div className="chart-box" style={{ height: Math.max(150, data.length * 40) }}>
			<ResponsiveContainer width="100%" height="100%">
				<BarChart data={data} layout="vertical" margin={{ top: 4, right: 18, bottom: 4, left: 4 }}>
					<XAxis type="number" hide />
					<YAxis
						type="category"
						dataKey="name"
						width={128}
						tickLine={false}
						axisLine={false}
						tick={AXIS_TICK}
					/>
					<Tooltip cursor={{ fill: 'var(--surface-hover)' }} />
					<Bar dataKey="value" radius={[0, 5, 5, 0]} barSize={16}>
						{data.map((d) => (
							<Cell key={d.name} fill={d.color} />
						))}
					</Bar>
				</BarChart>
			</ResponsiveContainer>
		</div>
	);
}
