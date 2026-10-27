package co.idrd.eventos;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Ventana principal: tabla de eventos, filtros y registro de correo. */
public class VentanaPrincipal extends JFrame {
    private static final String TODOS = "(Todos)";
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private final Config cfg;
    private final SuscripcionRepository repo;
    private final Notificador notificador;
    private final EmailService email;
    private volatile List<Evento> todos = new ArrayList<>();
    private Path rutaExcel;

    // filtros
    private final JComboBox<String> cbTipo = new JComboBox<>();
    private final JComboBox<String> cbDisciplina = new JComboBox<>();
    private final JComboBox<String> cbLocalidad = new JComboBox<>();
    private final JComboBox<String> cbMes = new JComboBox<>();
    private final JTextField tfBuscar = new JTextField(14);
    private final JCheckBox chkProximos = new JCheckBox("Solo próximos (desde hoy)");
    private boolean actualizando = false;

    // tabla
    private final EventosModel modelo = new EventosModel();
    private final JTable tabla = new JTable(modelo);
    private final JLabel lblConteo = new JLabel(" ");

    // correo
    private final JTextField tfCorreo = new JTextField(22);
    private final JSpinner spDias = new JSpinner(new SpinnerNumberModel(3, 1, 30, 1));
    private final JTextArea areaLog = new JTextArea(5, 40);

    public VentanaPrincipal(Config cfg) {
        super("Eventos deportivos IDRD");
        this.cfg = cfg;
        this.rutaExcel = Path.of(cfg.get("excel.ruta", "data/Eventos_IDRD.xlsx"));
        this.repo = new SuscripcionRepository(Path.of(cfg.get("suscripciones.ruta", "data/suscripciones.csv")));
        this.email = new EmailService(cfg);
        this.notificador = new Notificador(() -> todos, repo, email, this::log);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(panelFiltros(), BorderLayout.NORTH);
        add(panelTabla(), BorderLayout.CENTER);
        add(panelInferior(), BorderLayout.SOUTH);
        setSize(980, 700);
        setLocationRelativeTo(null);

        cargarExcel();
        iniciarNotificador();
    }

    // ---------------------------------------------------------------- filtros
    private JPanel panelFiltros() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Filtros de búsqueda"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        agregar(p, g, 0, 0, "Tipo de evento:", cbTipo);
        agregar(p, g, 2, 0, "Disciplina:", cbDisciplina);
        agregar(p, g, 4, 0, "Localidad:", cbLocalidad);
        agregar(p, g, 0, 1, "Mes:", cbMes);
        agregar(p, g, 2, 1, "Buscar texto:", tfBuscar);
        g.gridx = 4; g.gridy = 1; g.gridwidth = 2;
        p.add(chkProximos, g);

        JButton limpiar = new JButton("Limpiar filtros");
        limpiar.addActionListener(e -> limpiarFiltros());
        JButton otro = new JButton("Cargar otro Excel...");
        otro.addActionListener(e -> elegirExcel());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botones.add(limpiar);
        botones.add(otro);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 6; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(botones, g);

        cbTipo.addActionListener(e -> { if (!actualizando) { llenarDisciplinas(); aplicarFiltros(); } });
        for (JComboBox<String> cb : List.of(cbDisciplina, cbLocalidad, cbMes))
            cb.addActionListener(e -> { if (!actualizando) aplicarFiltros(); });
        chkProximos.addActionListener(e -> aplicarFiltros());
        tfBuscar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltros(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltros(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { aplicarFiltros(); }
        });
        return p;
    }

    private void agregar(JPanel p, GridBagConstraints g, int x, int y, String etiqueta, JComponent c) {
        g.gridx = x; g.gridy = y; g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
        p.add(new JLabel(etiqueta), g);
        g.gridx = x + 1;
        p.add(c, g);
    }

    private void llenarFiltros() {
        actualizando = true;
        llenar(cbTipo, new TreeSet<>(todos.stream().map(Evento::categoria).toList()));
        llenar(cbLocalidad, new TreeSet<>(todos.stream().map(Evento::localidad).toList()));
        cbMes.removeAllItems();
        cbMes.addItem(TODOS);
        for (String m : MESES) cbMes.addItem(m);
        llenarDisciplinas();
        actualizando = false;
    }

    /** Las disciplinas dependen del tipo elegido (p. ej. En equipo -> Futbol, Baloncesto...). */
    private void llenarDisciplinas() {
        boolean prev = actualizando;
        actualizando = true;
        String tipo = (String) cbTipo.getSelectedItem();
        TreeSet<String> ds = new TreeSet<>();
        for (Evento e : todos) if (tipo == null || TODOS.equals(tipo) || e.categoria().equals(tipo)) ds.add(e.disciplina());
        llenar(cbDisciplina, ds);
        actualizando = prev;
    }

    private void llenar(JComboBox<String> cb, TreeSet<String> valores) {
        Object anterior = cb.getSelectedItem();
        cb.removeAllItems();
        cb.addItem(TODOS);
        for (String v : valores) cb.addItem(v);
        if (anterior != null) cb.setSelectedItem(anterior);
        if (cb.getSelectedItem() == null) cb.setSelectedIndex(0);
    }

    private void limpiarFiltros() {
        actualizando = true;
        cbTipo.setSelectedIndex(0);
        llenarDisciplinas();
        cbDisciplina.setSelectedIndex(0);
        cbLocalidad.setSelectedIndex(0);
        cbMes.setSelectedIndex(0);
        tfBuscar.setText("");
        chkProximos.setSelected(false);
        actualizando = false;
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        String tipo = sel(cbTipo), dis = sel(cbDisciplina), loc = sel(cbLocalidad);
        int mes = cbMes.getSelectedIndex(); // 0 = todos
        String txt = tfBuscar.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate hoy = LocalDate.now();
        List<Evento> r = new ArrayList<>();
        for (Evento e : todos) {
            if (tipo != null && !e.categoria().equals(tipo)) continue;
            if (dis != null && !e.disciplina().equals(dis)) continue;
            if (loc != null && !e.localidad().equals(loc)) continue;
            if (mes > 0 && e.fecha().getMonthValue() != mes) continue;
            if (chkProximos.isSelected() && e.fecha().isBefore(hoy)) continue;
            if (!txt.isEmpty() && !(e.categoria() + " " + e.disciplina() + " " + e.localidad()).toLowerCase(Locale.ROOT).contains(txt)) continue;
            r.add(e);
        }
        modelo.setDatos(r);
        lblConteo.setText(r.size() + " de " + todos.size() + " eventos");
    }

    private String sel(JComboBox<String> cb) {
        String s = (String) cb.getSelectedItem();
        return (s == null || TODOS.equals(s)) ? null : s;
    }

    // ----------------------------------------------------------------- tabla
    private JPanel panelTabla() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(24);
        tabla.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        ((TableRowSorter<?>) tabla.getRowSorter()).setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
        tabla.setDefaultRenderer(Object.class, new Render());
        tabla.setDefaultRenderer(Integer.class, new Render());
        tabla.setDefaultRenderer(Long.class, new Render());
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);
        JPanel pie = new JPanel(new BorderLayout());
        pie.add(lblConteo, BorderLayout.WEST);
        JLabel leyenda = new JLabel("Naranja = empieza en 7 días o menos · Gris = ya pasó");
        leyenda.setForeground(Color.GRAY);
        pie.add(leyenda, BorderLayout.EAST);
        p.add(pie, BorderLayout.SOUTH);
        return p;
    }

    private static class Render extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
            int m = t.convertRowIndexToModel(fila);
            Evento e = ((EventosModel) t.getModel()).get(m);
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), e.fecha());
            if (col == 6) v = dias < 0 ? "Ya pasó" : dias == 0 ? "Hoy" : dias == 1 ? "Mañana" : dias + " días";
            Component c = super.getTableCellRendererComponent(t, v, sel, foco, fila, col);
            setHorizontalAlignment(col == 0 || col == 5 || col == 6 ? CENTER : LEFT);
            if (!sel) {
                c.setForeground(dias < 0 ? Color.GRAY : Color.BLACK);
                c.setBackground(dias >= 0 && dias <= 7 ? new Color(255, 232, 204) : Color.WHITE);
            }
            return c;
        }
    }

    private static class EventosModel extends AbstractTableModel {
        private static final String[] COLS = {"ID", "Fecha", "Tipo de evento", "Disciplina", "Localidad", "Asistentes", "Empieza"};
        private List<Evento> datos = new ArrayList<>();

        void setDatos(List<Evento> d) { datos = d; fireTableDataChanged(); }
        Evento get(int i) { return datos.get(i); }
        @Override public int getRowCount() { return datos.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) { case 0, 5 -> Integer.class; case 6 -> Long.class; default -> String.class; };
        }
        @Override public Object getValueAt(int r, int c) {
            Evento e = datos.get(r);
            return switch (c) {
                case 0 -> e.id();
                case 1 -> e.fecha().toString(); // yyyy-MM-dd: ordena bien como texto
                case 2 -> e.categoria();
                case 3 -> e.disciplina();
                case 4 -> e.localidad();
                case 5 -> e.asistentes();
                default -> ChronoUnit.DAYS.between(LocalDate.now(), e.fecha());
            };
        }
    }

    // ------------------------------------------------------- correo y avisos
    private JPanel panelInferior() {
        JPanel p = new JPanel(new BorderLayout(4, 6));
        JPanel reg = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        reg.setBorder(BorderFactory.createTitledBorder("Recibir aviso por correo (selecciona uno o varios eventos en la tabla)"));
        reg.add(new JLabel("Tu correo:"));
        reg.add(tfCorreo);
        reg.add(new JLabel("Avisarme"));
        reg.add(spDias);
        reg.add(new JLabel("día(s) antes"));
        JButton btn = new JButton("Registrar aviso");
        btn.addActionListener(e -> registrarAviso());
        JButton mis = new JButton("Mis avisos...");
        mis.addActionListener(e -> mostrarAvisos());
        reg.add(btn);
        reg.add(mis);
        p.add(reg, BorderLayout.NORTH);

        areaLog.setEditable(false);
        JScrollPane sp = new JScrollPane(areaLog);
        sp.setBorder(BorderFactory.createTitledBorder("Notificaciones"));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void registrarAviso() {
        String correo = tfCorreo.getText().trim();
        if (!SuscripcionRepository.correoValido(correo)) {
            JOptionPane.showMessageDialog(this, "Escribe un correo electrónico válido (ejemplo: nombre@gmail.com).", "Correo no válido", JOptionPane.WARNING_MESSAGE);
            tfCorreo.requestFocus();
            return;
        }
        int[] filas = tabla.getSelectedRows();
        if (filas.length == 0) {
            JOptionPane.showMessageDialog(this, "Primero selecciona en la tabla el evento (o eventos) del que quieres recibir aviso.", "Falta seleccionar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dias = (Integer) spDias.getValue();
        int n = 0, pasados = 0;
        for (int f : filas) {
            Evento e = modelo.get(tabla.convertRowIndexToModel(f));
            if (e.fecha().isBefore(LocalDate.now())) { pasados++; continue; }
            repo.agregar(correo, e.id(), dias);
            log("Registrado " + correo + " -> " + e.titulo() + " (" + e.fecha().format(Notificador.FECHA) + "), aviso " + dias + " día(s) antes.");
            n++;
        }
        String msg = n + " aviso(s) registrado(s) para " + correo + ".";
        if (pasados > 0) msg += "\n" + pasados + " evento(s) ya pasaron y se omitieron.";
        if (email.esSimulacion()) msg += "\n\nNota: no hay correo SMTP configurado (config.properties); los envíos serán simulados.";
        JOptionPane.showMessageDialog(this, msg, "Listo", JOptionPane.INFORMATION_MESSAGE);
        // Si el evento ya esta dentro del plazo, avisa de inmediato
        Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; })
                .submit(notificador::revisar);
    }

    private void mostrarAvisos() {
        String correo = tfCorreo.getText().trim();
        List<Suscripcion> lista = repo.listar(correo);
        if (lista.isEmpty()) {
            JOptionPane.showMessageDialog(this, correo.isEmpty() ? "No hay avisos registrados." : "No hay avisos para " + correo + ".");
            return;
        }
        DefaultListModel<String> lm = new DefaultListModel<>();
        for (Suscripcion s : lista) {
            Evento e = todos.stream().filter(x -> x.id() == s.eventoId).findFirst().orElse(null);
            lm.addElement(s.correo + "  |  " + (e == null ? "evento #" + s.eventoId : e.titulo() + " " + e.fecha().format(Notificador.FECHA))
                    + "  |  " + s.diasAntes + " día(s) antes  |  " + (s.enviado ? "ya enviado" : "pendiente"));
        }
        JList<String> jl = new JList<>(lm);
        JScrollPane sp = new JScrollPane(jl);
        sp.setPreferredSize(new Dimension(640, 220));
        Object[] opciones = {"Eliminar seleccionado", "Cerrar"};
        int r = JOptionPane.showOptionDialog(this, sp, correo.isEmpty() ? "Todos los avisos" : "Avisos de " + correo,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[1]);
        if (r == 0 && jl.getSelectedIndex() >= 0) {
            Suscripcion s = lista.get(jl.getSelectedIndex());
            repo.eliminar(s.correo, s.eventoId);
            log("Aviso eliminado: " + s.correo + " -> evento #" + s.eventoId);
        }
    }

    private void log(String texto) {
        String linea = java.time.LocalTime.now().withNano(0) + "  " + texto + "\n";
        SwingUtilities.invokeLater(() -> {
            areaLog.append(linea);
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    // ------------------------------------------------------------------ excel
    private void cargarExcel() {
        try {
            todos = ExcelRepository.cargar(rutaExcel, cfg.getInt("anio.por.defecto", LocalDate.now().getYear()));
            llenarFiltros();
            aplicarFiltros();
            log("Excel cargado: " + rutaExcel + " (" + todos.size() + " eventos)."
                    + (email.esSimulacion() ? " Modo SIMULACIÓN de correo (sin SMTP configurado)." : " Envío de correo SMTP activo."));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo cargar el Excel", JOptionPane.ERROR_MESSAGE);
            log("ERROR: " + ex.getMessage());
        }
    }

    private void elegirExcel() {
        JFileChooser fc = new JFileChooser(new java.io.File("."));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rutaExcel = fc.getSelectedFile().toPath();
            cargarExcel();
        }
    }

    /** Revisa avisos al abrir y luego cada N minutos mientras el programa este abierto. */
    private void iniciarNotificador() {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notificador");
            t.setDaemon(true);
            return t;
        });
        int min = Math.max(1, cfg.getInt("notificador.intervalo.minutos", 30));
        ses.scheduleWithFixedDelay(() -> {
            try { notificador.revisar(); } catch (Exception e) { log("ERROR: " + e.getMessage()); }
        }, 2, min * 60L, TimeUnit.SECONDS);
    }
}
