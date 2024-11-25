package io.mosip.kernel.uingenerator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request dto for getting uins in bulk
 *
 * @author Lalith Kota
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetBulkUinsRequestDto {

	/**
	 * The number of uins required
	 */
	private int count;

}
