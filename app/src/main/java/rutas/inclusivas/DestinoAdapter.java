package rutas.inclusivas;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DestinoAdapter extends RecyclerView.Adapter<DestinoAdapter.DestinoViewHolder> {

    private List<Destino> destinos;
    private OnDestinoClickListener listener;

    // Interfaz para comunicar qué destino seleccionó el usuario
    public interface OnDestinoClickListener {
        void onDestinoClick(Destino destino);
    }

    // Constructor
    public DestinoAdapter(List<Destino> destinos, OnDestinoClickListener listener) {
        this.destinos = destinos;
        this.listener = listener;
    }

    // Crea visualmente una fila usando item_destino.xml
    @NonNull
    @Override
    public DestinoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_destino, parent, false);
        return new DestinoViewHolder(vista);
    }

    // Coloca los datos de un destino en la fila
    @Override
    public void onBindViewHolder(@NonNull DestinoViewHolder holder, int position) {

        Destino destino = destinos.get(position);

        holder.tvNombreDestino.setText(destino.getNombre());
        holder.tvDireccionDestino.setText(destino.getDireccion());

        // Detecta cuando el usuario toca una fila
        holder.itemView.setOnClickListener(v -> {

            int posicionActual = holder.getBindingAdapterPosition();

            if (posicionActual != RecyclerView.NO_POSITION) {
                Destino destinoSeleccionado = destinos.get(posicionActual);
                listener.onDestinoClick(destinoSeleccionado);
            }
        });
    }

    // Informa cuántos destinos tiene la lista
    @Override
    public int getItemCount() {
        return destinos.size();
    }

    // Representa los controles visuales de una fila
    public static class DestinoViewHolder extends RecyclerView.ViewHolder {

        TextView tvNombreDestino;
        TextView tvDireccionDestino;

        public DestinoViewHolder(@NonNull View itemView) {
            super(itemView);

            tvNombreDestino = itemView.findViewById(R.id.tvNombreDestino);
            tvDireccionDestino = itemView.findViewById(R.id.tvDireccionDestino);
        }
    }

    // Permite reemplazar los resultados cuando llega otra búsqueda
    public void actualizarDestinos(List<Destino> nuevosDestinos) {
        destinos.clear();
        destinos.addAll(nuevosDestinos);
        notifyDataSetChanged();
    }
}
