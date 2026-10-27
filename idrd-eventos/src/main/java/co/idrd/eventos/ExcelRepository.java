package co.idrd.eventos;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Lee los eventos de la hoja "Datos" del Excel.
 * Las columnas se buscan por el NOMBRE del encabezado (no por posicion):
 *   ID, Categoria, Disciplina, Mes (1-12), Localidad, Asistentes
 * Opcionales: "Dia" y "Anio" (si no existen: dia 1 y anio por defecto).
 */
public class ExcelRepository {

    public static List<Evento> cargar(Path ruta, int anioPorDefecto) throws IOException {
        if (!Files.exists(ruta)) {
            throw new IOException("No se encontro el archivo Excel: " + ruta.toAbsolutePath());
        }
        List<Evento> eventos = new ArrayList<>();
        try (InputStream in = Files.newInputStream(ruta); Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheet("Datos");
            if (hoja == null) hoja = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            // 1) Buscar la fila de encabezados
            Map<String, Integer> col = null;
            int filaEnc = -1;
            for (int r = hoja.getFirstRowNum(); r <= Math.min(hoja.getLastRowNum(), 30); r++) {
                Row row = hoja.getRow(r);
                if (row == null) continue;
                Map<String, Integer> m = new HashMap<>();
                for (Cell c : row) m.put(normalizar(fmt.formatCellValue(c)), c.getColumnIndex());
                if (m.containsKey("id") && m.containsKey("categoria")) {
                    col = m;
                    filaEnc = r;
                    break;
                }
            }
            if (col == null) throw new IOException("No se encontro la fila de encabezados (ID, Categoria, ...) en la hoja " + hoja.getSheetName());

            int cId = col.get("id");
            int cCat = col.get("categoria");
            int cDis = buscar(col, "disciplina");
            int cMes = buscar(col, "mes");
            int cLoc = buscar(col, "localidad");
            int cAsi = buscar(col, "asistentes");
            int cDia = col.getOrDefault("dia", -1);
            int cAnio = col.containsKey("anio") ? col.get("anio") : col.getOrDefault("ano", -1);

            // 2) Leer filas
            for (int r = filaEnc + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) continue;
                Integer id = entero(row, cId, fmt);
                Integer mes = entero(row, cMes, fmt);
                String cat = texto(row, cCat, fmt);
                if (id == null || mes == null || mes < 1 || mes > 12 || cat.isEmpty()) continue;
                Integer dia = cDia >= 0 ? entero(row, cDia, fmt) : null;
                Integer anio = cAnio >= 0 ? entero(row, cAnio, fmt) : null;
                int a = anio != null ? anio : anioPorDefecto;
                int d = dia != null ? dia : 1;
                d = Math.max(1, Math.min(d, YearMonth.of(a, mes).lengthOfMonth()));
                Integer asi = entero(row, cAsi, fmt);
                eventos.add(new Evento(id, cat, texto(row, cDis, fmt), texto(row, cLoc, fmt),
                        asi == null ? 0 : asi, LocalDate.of(a, mes, d)));
            }
        }
        eventos.sort(Comparator.comparing(Evento::fecha).thenComparing(Evento::id));
        return eventos;
    }

    private static int buscar(Map<String, Integer> col, String prefijo) throws IOException {
        for (var e : col.entrySet()) if (e.getKey().startsWith(prefijo)) return e.getValue();
        throw new IOException("Falta la columna '" + prefijo + "' en el Excel");
    }

    private static String texto(Row row, int c, DataFormatter fmt) {
        Cell cell = row.getCell(c);
        return cell == null ? "" : fmt.formatCellValue(cell).trim();
    }

    private static Integer entero(Row row, int c, DataFormatter fmt) {
        Cell cell = row.getCell(c);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return (int) Math.round(cell.getNumericCellValue());
            return Integer.parseInt(fmt.formatCellValue(cell).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizar(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.trim().toLowerCase(Locale.ROOT);
    }
}
